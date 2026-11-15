package com.otpforwarder.app.forwarding

import com.otpforwarder.app.data.repository.FilterMode
import com.otpforwarder.app.data.repository.UserSettings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface FilterDecision {
    data class Allow(
        val matchedPattern: String,
        val formattedMessage: String,
        val targetRecipient: String
    ) : FilterDecision

    data class Blocked(
        val reason: String,
        val ruleType: BlockReason
    ) : FilterDecision
}

enum class BlockReason {
    MASTER_SWITCH_OFF,
    EMPTY_RECIPIENT,
    INVALID_RECIPIENT_FORMAT,
    LOOP_PREVENTION,
    NOT_IN_WHITELIST,
    IN_BLACKLIST,
    NO_REGEX_MATCH,
    INVALID_REGEX
}

object FilterEngine {

    /**
     * Evaluates an incoming SMS against all active rules and returns a [FilterDecision].
     * Completely decoupled from Android framework for fast JVM unit testing.
     */
    fun evaluate(
        rawSender: String,
        rawMessage: String,
        settings: UserSettings
    ): FilterDecision {
        val sender = rawSender.trim()
        val message = rawMessage.trim()

        // 1. Master toggle check
        if (!settings.isForwardingEnabled) {
            return FilterDecision.Blocked(
                reason = "Forwarding is globally disabled in settings",
                ruleType = BlockReason.MASTER_SWITCH_OFF
            )
        }

        // 2. Recipient configuration check
        val recipient = settings.recipientPhoneNumber.trim()
        if (recipient.isEmpty()) {
            return FilterDecision.Blocked(
                reason = "Recipient phone number is not configured",
                ruleType = BlockReason.EMPTY_RECIPIENT
            )
        }

        if (!isValidPhoneNumber(recipient)) {
            return FilterDecision.Blocked(
                reason = "Recipient phone number '$recipient' is invalid. Must include country code (e.g., +1... or +91...)",
                ruleType = BlockReason.INVALID_RECIPIENT_FORMAT
            )
        }

        // 3. Loop prevention (Sender == Recipient)
        if (isPotentialLoop(sender, recipient)) {
            return FilterDecision.Blocked(
                reason = "Potential forward loop detected: Sender '$sender' matches recipient '$recipient'",
                ruleType = BlockReason.LOOP_PREVENTION
            )
        }

        // 4. Sender Whitelist / Blacklist check
        when (settings.filterMode) {
            FilterMode.WHITELIST -> {
                val matchesWhitelist = matchesSenderList(sender, settings.senderList)
                if (!matchesWhitelist) {
                    return FilterDecision.Blocked(
                        reason = "Sender '$sender' is not in the approved whitelist",
                        ruleType = BlockReason.NOT_IN_WHITELIST
                    )
                }
            }
            FilterMode.BLACKLIST -> {
                val matchesBlacklist = matchesSenderList(sender, settings.senderList)
                if (matchesBlacklist) {
                    return FilterDecision.Blocked(
                        reason = "Sender '$sender' matches blocked blacklist rule",
                        ruleType = BlockReason.IN_BLACKLIST
                    )
                }
            }
            FilterMode.DISABLED -> {
                // No sender filter applied
            }
        }

        // 5. Keyword / Regex OTP pattern matching
        val patternString = settings.otpRegexPattern.ifBlank { UserSettings.DEFAULT_OTP_REGEX }
        val regex = try {
            Regex(patternString, setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
        } catch (e: Exception) {
            return FilterDecision.Blocked(
                reason = "Invalid custom regex pattern: ${e.message}",
                ruleType = BlockReason.INVALID_REGEX
            )
        }

        val matchResult = regex.find(message)
        if (matchResult == null) {
            return FilterDecision.Blocked(
                reason = "Message did not match OTP keyword or regex pattern",
                ruleType = BlockReason.NO_REGEX_MATCH
            )
        }

        // 6. Format final message with prefix
        val formatted = formatForwardedMessage(
            prefixTemplate = settings.customPrefix,
            sender = sender,
            originalMessage = message
        )

        return FilterDecision.Allow(
            matchedPattern = matchResult.value,
            formattedMessage = formatted,
            targetRecipient = recipient
        )
    }

    /**
     * Checks if sender matches any item in the sender list.
     * Supports case-insensitive substring matching (e.g., "AMAZON" matches "VM-AMAZON-A").
     */
    fun matchesSenderList(sender: String, rules: Set<String>): Boolean {
        if (rules.isEmpty()) return false
        val cleanSender = sender.uppercase().replace(Regex("[^A-Z0-9]"), "")
        return rules.any { rule ->
            val cleanRule = rule.trim().uppercase().replace(Regex("[^A-Z0-9]"), "")
            cleanRule.isNotEmpty() && (cleanSender.contains(cleanRule) || sender.contains(rule.trim(), ignoreCase = true))
        }
    }

    /**
     * Validates phone number format: allows international format +[country_code][number], 7 to 15 digits.
     */
    fun isValidPhoneNumber(phone: String): Boolean {
        val sanitized = phone.replace(Regex("[\\s\\-\\(\\)]"), "")
        val phoneRegex = Regex("^\\+?[1-9]\\d{6,14}$")
        return phoneRegex.matches(sanitized)
    }

    /**
     * Detects if the sender and recipient are the same number to avoid infinite forwarding loops.
     */
    private fun isPotentialLoop(sender: String, recipient: String): Boolean {
        val cleanSender = sender.replace(Regex("[^0-9]"), "")
        val cleanRecipient = recipient.replace(Regex("[^0-9]"), "")
        if (cleanSender.length >= 7 && cleanRecipient.length >= 7) {
            return cleanSender.endsWith(cleanRecipient) || cleanRecipient.endsWith(cleanSender)
        }
        return false
    }

    /**
     * Formats outgoing message using prefix template.
     * Supported variables: {sender}, {time}
     */
    fun formatForwardedMessage(
        prefixTemplate: String,
        sender: String,
        originalMessage: String
    ): String {
        val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        val formattedPrefix = prefixTemplate
            .replace("{sender}", sender, ignoreCase = true)
            .replace("{time}", timeStr, ignoreCase = true)

        return "$formattedPrefix$originalMessage".trim()
    }

    /**
     * Masks sensitive numeric OTP digits (e.g., 4 to 8 digits) for privacy in logs.
     */
    fun maskOtpDigits(message: String): String {
        // Replace standalone 4 to 8 digit codes with bullet points
        return message.replace(Regex("\\b\\d{4,8}\\b")) { match ->
            "•".repeat(match.value.length)
        }
    }
}
