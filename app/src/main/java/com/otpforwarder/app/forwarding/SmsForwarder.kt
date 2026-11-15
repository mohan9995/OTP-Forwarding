package com.otpforwarder.app.forwarding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat

data class SimSubscriptionInfo(
    val subscriptionId: Int,
    val simSlotIndex: Int,
    val displayName: String,
    val carrierName: String,
    val number: String? = null
)

sealed interface SendResult {
    data class Success(val partsSent: Int, val subscriptionId: Int?) : SendResult
    data class Failure(val error: String, val exception: Throwable? = null) : SendResult
}

object SmsForwarder {

    /**
     * Obtains the correct instance of [SmsManager] taking into account API levels and dual-SIM subscription IDs.
     */
    fun getSmsManager(context: Context, subscriptionId: Int = -1): SmsManager {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val systemSmsManager = context.getSystemService(SmsManager::class.java)
            if (subscriptionId != -1 && subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                systemSmsManager.createForSubscriptionId(subscriptionId)
            } else {
                systemSmsManager
            }
        } else {
            if (subscriptionId != -1 && subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                @Suppress("DEPRECATION")
                SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
        }
    }

    /**
     * Dispatches an SMS to the destination number. Handles multi-part message splitting automatically.
     */
    fun sendSms(
        context: Context,
        destinationAddress: String,
        message: String,
        subscriptionId: Int = -1
    ): SendResult {
        // Permission check
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return SendResult.Failure("SEND_SMS permission has not been granted by the user.")
        }

        return try {
            val smsManager = getSmsManager(context, subscriptionId)
            val parts = smsManager.divideMessage(message)

            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(
                    destinationAddress,
                    null,
                    parts,
                    null,
                    null
                )
            } else {
                smsManager.sendTextMessage(
                    destinationAddress,
                    null,
                    message,
                    null,
                    null
                )
            }

            SendResult.Success(
                partsSent = parts.size,
                subscriptionId = if (subscriptionId != -1) subscriptionId else null
            )
        } catch (e: SecurityException) {
            SendResult.Failure("SecurityException while sending SMS: ${e.localizedMessage}", e)
        } catch (e: IllegalArgumentException) {
            SendResult.Failure("Invalid destination or message: ${e.localizedMessage}", e)
        } catch (e: Exception) {
            SendResult.Failure("Unexpected error sending SMS: ${e.localizedMessage}", e)
        }
    }

    /**
     * Discovers active SIM subscriptions for dual-SIM devices.
     */
    fun getActiveSubscriptions(context: Context): List<SimSubscriptionInfo> {
        val hasPhoneStatePermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPhoneStatePermission) {
            return emptyList()
        }

        return try {
            val subscriptionManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.getSystemService(SubscriptionManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SubscriptionManager.from(context)
            }

            val activeList: List<SubscriptionInfo>? = subscriptionManager?.activeSubscriptionInfoList
            activeList?.map { info ->
                SimSubscriptionInfo(
                    subscriptionId = info.subscriptionId,
                    simSlotIndex = info.simSlotIndex,
                    displayName = info.displayName?.toString() ?: "SIM ${info.simSlotIndex + 1}",
                    carrierName = info.carrierName?.toString() ?: "Unknown Carrier",
                    number = info.number
                )
            } ?: emptyList()
        } catch (e: SecurityException) {
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
