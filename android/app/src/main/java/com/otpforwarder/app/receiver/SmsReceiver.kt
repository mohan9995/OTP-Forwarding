package com.otpforwarder.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.otpforwarder.app.data.local.AppDatabase
import com.otpforwarder.app.data.local.ForwardStatus
import com.otpforwarder.app.data.local.LogEntity
import com.otpforwarder.app.data.repository.SettingsRepository
import com.otpforwarder.app.forwarding.BlockReason
import com.otpforwarder.app.forwarding.FilterDecision
import com.otpforwarder.app.forwarding.FilterEngine
import com.otpforwarder.app.forwarding.SendResult
import com.otpforwarder.app.forwarding.SmsForwarder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) {
            return
        }

        // BroadcastReceiver runs on the main thread and has a 10-second timeout.
        // goAsync() signals the OS that we are continuing background execution.
        val pendingResult = goAsync()
        val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        receiverScope.launch {
            try {
                // Group multi-part SMS PDUs by sender to concatenate complete message bodies
                val groupedBySender = messages.groupBy { it.originatingAddress.orEmpty() }

                val settingsRepo = SettingsRepository(context)
                val settings = settingsRepo.getSettingsSnapshot()
                val database = AppDatabase.getInstance(context)
                val logDao = database.logDao()

                for ((sender, pduList) in groupedBySender) {
                    if (sender.isBlank()) continue

                    // Concatenate multi-part SMS parts into a single string
                    val fullMessage = pduList.joinToString(separator = "") { it.messageBody.orEmpty() }

                    // Evaluate with FilterEngine
                    val decision = FilterEngine.evaluate(
                        rawSender = sender,
                        rawMessage = fullMessage,
                        settings = settings
                    )

                    val bodyToStore = if (settings.maskSensitiveData) {
                        FilterEngine.maskOtpDigits(fullMessage)
                    } else {
                        fullMessage
                    }

                    when (decision) {
                        is FilterDecision.Allow -> {
                            Log.d(TAG, "OTP Matched. Forwarding SMS from $sender to ${decision.targetRecipient}")

                            val sendResult = SmsForwarder.sendSms(
                                context = context,
                                destinationAddress = decision.targetRecipient,
                                message = decision.formattedMessage,
                                subscriptionId = settings.selectedSubscriptionId
                            )

                            when (sendResult) {
                                is SendResult.Success -> {
                                    val forwardedToStore = if (settings.maskSensitiveData) {
                                        FilterEngine.maskOtpDigits(decision.formattedMessage)
                                    } else {
                                        decision.formattedMessage
                                    }

                                    logDao.insertLog(
                                        LogEntity(
                                            sender = sender,
                                            recipient = decision.targetRecipient,
                                            originalMessage = bodyToStore,
                                            forwardedMessage = forwardedToStore,
                                            timestamp = System.currentTimeMillis(),
                                            status = ForwardStatus.FORWARDED,
                                            failureReason = null,
                                            simSlot = sendResult.subscriptionId
                                        )
                                    )
                                }
                                is SendResult.Failure -> {
                                    Log.e(TAG, "Failed to forward SMS: ${sendResult.error}")
                                    logDao.insertLog(
                                        LogEntity(
                                            sender = sender,
                                            recipient = decision.targetRecipient,
                                            originalMessage = bodyToStore,
                                            forwardedMessage = null,
                                            timestamp = System.currentTimeMillis(),
                                            status = ForwardStatus.FAILED,
                                            failureReason = sendResult.error,
                                            simSlot = settings.selectedSubscriptionId
                                        )
                                    )
                                }
                            }
                        }

                        is FilterDecision.Blocked -> {
                            Log.d(TAG, "SMS from $sender blocked: ${decision.reason} (${decision.ruleType})")

                            // Log blocked attempts if it was due to filter/whitelist/blacklist/loop
                            // (Avoid logging every regular text if it simply didn't match OTP regex or master switch is off)
                            if (decision.ruleType != BlockReason.NO_REGEX_MATCH &&
                                decision.ruleType != BlockReason.MASTER_SWITCH_OFF
                            ) {
                                logDao.insertLog(
                                    LogEntity(
                                        sender = sender,
                                        recipient = settings.recipientPhoneNumber,
                                        originalMessage = bodyToStore,
                                        forwardedMessage = null,
                                        timestamp = System.currentTimeMillis(),
                                        status = ForwardStatus.BLOCKED,
                                        failureReason = decision.reason,
                                        simSlot = null
                                    )
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in SmsReceiver background processing", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
