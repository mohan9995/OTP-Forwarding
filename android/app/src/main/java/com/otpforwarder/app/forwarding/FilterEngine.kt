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

        // 6. Format final message with prefix or carrier-safe cleaner
        val formatted = formatForwardedMessage(
            prefixTemplate = settings.customPrefix,
            sender = sender,
            originalMessage = message,
            carrierSafeMode = settings.carrierSafeMode,
            spaceSeparatedDigits = settings.spaceSeparatedDigits
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
     * Formats outgoing message using prefix template or carrier-safe cleaner.
     * Supported variables: {sender}, {time}
     */
    fun formatForwardedMessage(
        prefixTemplate: String,
        sender: String,
        originalMessage: String,
        carrierSafeMode: Boolean = true,
        spaceSeparatedDigits: Boolean = true
    ): String {
        if (!carrierSafeMode) {
            val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            val formattedPrefix = prefixTemplate
                .replace("{sender}", sender, ignoreCase = true)
                .replace("{time}", timeStr, ignoreCase = true)

            return "$formattedPrefix$originalMessage".trim()
        }

        // Telecom Carrier-Safe Mode:
        // Extracts the OTP code and merchant/service while omitting bank template triggers
        val (otp, merchant) = extractOtpAndMerchant(originalMessage)
        val cleanSender = cleanSenderName(sender)

        val builder = StringBuilder("[FWD]")
        if (otp != null) {
            val formattedOtp = if (spaceSeparatedDigits) otp.toCharArray().joinToString(" ") else otp
            builder.append(" Code: ").append(formattedOtp)
        } else {
            builder.append(" ").append(originalMessage.take(80))
        }

        if (!merchant.isNullOrBlank()) {
            builder.append(" for ").append(merchant)
        }

        if (cleanSender.isNotBlank()) {
            builder.append(" (").append(cleanSender).append(")")
        }

        return builder.toString().trim()
    }

    /**
     * Extracts numerical OTP code (4-8 digits) and associated merchant/entity from message.
     */
    fun extractOtpAndMerchant(text: String): Pair<String?, String?> {
        var otp: String? = null

        // Pattern 1: Leading code, e.g. "232066 is One-Time Password", "271110 is SECRET OTP"
        val p1 = Regex("^\\s*(\\d{4,8})\\b(?:\\s+is\\s+)", RegexOption.IGNORE_CASE).find(text)
        if (p1 != null) {
            otp = p1.groupValues[1]
        }

        // Pattern 2: Prefix-based code, e.g. "G-847291 is your Google..."
        if (otp == null) {
            val p2 = Regex("(?:^|\\b)[A-Za-z]?-?(\\d{4,8})\\b\\s+is\\s+", RegexOption.IGNORE_CASE).find(text)
            if (p2 != null) {
                otp = p2.groupValues[1]
            }
        }

        // Pattern 3: Labeled code, e.g. "OTP is 584920", "code: 123456", "OTP: 918273", "OTP for Rajmarg Yatra is 9584"
        if (otp == null) {
            val p3 = Regex("(?:otp|code|password|passcode|secret)[^\\d\\n]{1,40}\\b(\\d{4,8})\\b", RegexOption.IGNORE_CASE).find(text)
            if (p3 != null) {
                otp = p3.groupValues[1]
            }
        }

        // Pattern 4: Code followed by purpose, e.g. "584920 is your OTP"
        if (otp == null) {
            val p4 = Regex("\\b(\\d{4,8})\\b\\s+(?:is\\s+your|is\\s+the|to\\s+validate|for\\s+login)", RegexOption.IGNORE_CASE).find(text)
            if (p4 != null) {
                otp = p4.groupValues[1]
            }
        }

        // Pattern 5: Any standalone 4-8 digit number not belonging to a card (XX...) or amount
        if (otp == null) {
            val numbers = Regex("\\b(\\d{4,8})\\b").findAll(text).map { it.groupValues[1] }.toList()
            val filtered = numbers.filter { n ->
                !text.contains("XX$n", ignoreCase = true) &&
                !text.contains("INR $n", ignoreCase = true) &&
                !text.contains("Rs $n", ignoreCase = true) &&
                !text.contains("Rs. $n", ignoreCase = true)
            }
            if (filtered.isNotEmpty()) {
                otp = filtered.first()
            }
        }

        // 2. Extract Merchant / Context
        var merchant: String? = null
        val m1 = Regex("(?:towards|at)\\s+([A-Za-z0-9_.\n -]{2,20}?)(?:\\s+(?:using|on|card|\\.|\\bI\\b|for|at|\\d)|$)", RegexOption.IGNORE_CASE).find(text)
        if (m1 != null) {
            merchant = m1.groupValues[1].trim()
        } else {
            val m2 = Regex("(?:for login to|login to|to login to)\\s+([A-Za-z0-9_.\n -]{2,20}?)(?:\\s+(?:account|NetBanking|\\.)|$)", RegexOption.IGNORE_CASE).find(text)
            if (m2 != null) {
                merchant = m2.groupValues[1].trim()
            } else {
                val m3 = Regex("(?:for)\\s+([A-Za-z0-9_.\n -]{2,20}?)(?:\\s+(?:Pay|account|order|trip|ride|is|\\.)|$)", RegexOption.IGNORE_CASE).find(text)
                if (m3 != null) {
                    val candidate = m3.groupValues[1].trim()
                    if (!candidate.contains("txn", ignoreCase = true) && !candidate.contains("inr", ignoreCase = true)) {
                        merchant = candidate
                    }
                }
            }
        }

        if (merchant != null) {
            merchant = merchant.replace(Regex("\\s+[A-Z]$"), "").trim()
        }

        return Pair(otp, merchant)
    }

    /**
     * Cleans telecom routing prefixes from sender: e.g. "AD-ICICIO-T" -> "ICICIO", "JM-AXISBK-T" -> "AXISBK".
     */
    fun cleanSenderName(sender: String): String {
        return sender
            .replace(Regex("^[A-Za-z]{2}-"), "")
            .replace(Regex("-[A-Za-z]$"), "")
            .trim()
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
