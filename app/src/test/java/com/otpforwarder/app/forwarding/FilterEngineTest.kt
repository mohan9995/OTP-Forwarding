package com.otpforwarder.app.forwarding

import com.otpforwarder.app.data.repository.FilterMode
import com.otpforwarder.app.data.repository.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterEngineTest {

    private val baseSettings = UserSettings(
        isForwardingEnabled = true,
        recipientPhoneNumber = "+1234567890",
        otpRegexPattern = UserSettings.DEFAULT_OTP_REGEX,
        filterMode = FilterMode.DISABLED,
        senderList = emptySet(),
        customPrefix = "[FWD from {sender}]: "
    )

    @Test
    fun evaluate_whenMasterSwitchDisabled_returnsBlocked() {
        val settings = baseSettings.copy(isForwardingEnabled = false)
        val decision = FilterEngine.evaluate(
            rawSender = "AMAZON",
            rawMessage = "Your OTP is 123456",
            settings = settings
        )

        assertTrue(decision is FilterDecision.Blocked)
        assertEquals(BlockReason.MASTER_SWITCH_OFF, (decision as FilterDecision.Blocked).ruleType)
    }

    @Test
    fun evaluate_whenRecipientEmpty_returnsBlocked() {
        val settings = baseSettings.copy(recipientPhoneNumber = "")
        val decision = FilterEngine.evaluate(
            rawSender = "AMAZON",
            rawMessage = "Your OTP is 123456",
            settings = settings
        )

        assertTrue(decision is FilterDecision.Blocked)
        assertEquals(BlockReason.EMPTY_RECIPIENT, (decision as FilterDecision.Blocked).ruleType)
    }

    @Test
    fun evaluate_whenRecipientInvalidFormat_returnsBlocked() {
        val settings = baseSettings.copy(recipientPhoneNumber = "123")
        val decision = FilterEngine.evaluate(
            rawSender = "AMAZON",
            rawMessage = "Your OTP is 123456",
            settings = settings
        )

        assertTrue(decision is FilterDecision.Blocked)
        assertEquals(BlockReason.INVALID_RECIPIENT_FORMAT, (decision as FilterDecision.Blocked).ruleType)
    }

    @Test
    fun evaluate_whenSenderMatchesRecipient_blocksForLoopPrevention() {
        val settings = baseSettings.copy(recipientPhoneNumber = "+1234567890")
        val decision = FilterEngine.evaluate(
            rawSender = "+1234567890",
            rawMessage = "Your OTP is 123456",
            settings = settings
        )

        assertTrue(decision is FilterDecision.Blocked)
        assertEquals(BlockReason.LOOP_PREVENTION, (decision as FilterDecision.Blocked).ruleType)
    }

    @Test
    fun evaluate_whenWhitelistMode_rejectsNonWhitelistedSender() {
        val settings = baseSettings.copy(
            filterMode = FilterMode.WHITELIST,
            senderList = setOf("AMAZON", "GOOGLE")
        )
        val decision = FilterEngine.evaluate(
            rawSender = "NETFLIX",
            rawMessage = "Your OTP is 123456",
            settings = settings
        )

        assertTrue(decision is FilterDecision.Blocked)
        assertEquals(BlockReason.NOT_IN_WHITELIST, (decision as FilterDecision.Blocked).ruleType)
    }

    @Test
    fun evaluate_whenWhitelistMode_allowsWhitelistedSender() {
        val settings = baseSettings.copy(
            filterMode = FilterMode.WHITELIST,
            senderList = setOf("AMAZON", "GOOGLE")
        )
        val decision = FilterEngine.evaluate(
            rawSender = "VM-AMAZON-A",
            rawMessage = "Your OTP is 849201",
            settings = settings
        )

        assertTrue(decision is FilterDecision.Allow)
        val allow = decision as FilterDecision.Allow
        assertTrue(allow.formattedMessage.contains("VM-AMAZON-A"))
        assertTrue(allow.formattedMessage.contains("849201"))
    }

    @Test
    fun evaluate_whenBlacklistMode_blocksBlacklistedSender() {
        val settings = baseSettings.copy(
            filterMode = FilterMode.BLACKLIST,
            senderList = setOf("HDFCBK", "SBIINB")
        )
        val decision = FilterEngine.evaluate(
            rawSender = "AD-HDFCBK",
            rawMessage = "Your OTP for transaction is 998811",
            settings = settings
        )

        assertTrue(decision is FilterDecision.Blocked)
        assertEquals(BlockReason.IN_BLACKLIST, (decision as FilterDecision.Blocked).ruleType)
    }

    @Test
    fun evaluate_whenBlacklistMode_allowsNonBlacklistedSender() {
        val settings = baseSettings.copy(
            filterMode = FilterMode.BLACKLIST,
            senderList = setOf("HDFCBK", "SBIINB")
        )
        val decision = FilterEngine.evaluate(
            rawSender = "UBER",
            rawMessage = "Your Uber verification code is 4455",
            settings = settings
        )

        assertTrue(decision is FilterDecision.Allow)
    }

    @Test
    fun evaluate_whenMessageDoesNotMatchOtpRegex_returnsBlocked() {
        val decision = FilterEngine.evaluate(
            rawSender = "FRIEND",
            rawMessage = "Hey, are we still meeting up for dinner tonight?",
            settings = baseSettings
        )

        assertTrue(decision is FilterDecision.Blocked)
        assertEquals(BlockReason.NO_REGEX_MATCH, (decision as FilterDecision.Blocked).ruleType)
    }

    @Test
    fun evaluate_whenValidOtpKeywords_returnsAllow() {
        val testCases = listOf(
            "Your login OTP is 556677. Do not share.",
            "Use verification code 1234 to verify your phone number.",
            "Security code: 998211. Valid for 10 minutes.",
            "Your one time password is 332211."
        )

        for (msg in testCases) {
            val decision = FilterEngine.evaluate(
                rawSender = "SERVICE",
                rawMessage = msg,
                settings = baseSettings
            )
            assertTrue("Expected allow for message: $msg", decision is FilterDecision.Allow)
        }
    }

    @Test
    fun maskOtpDigits_masksNumericCodes() {
        val original = "Your Amazon OTP is 482910, valid for 5 min."
        val masked = FilterEngine.maskOtpDigits(original)
        assertEquals("Your Amazon OTP is ••••••, valid for 5 min.", masked)
    }

    @Test
    fun isValidPhoneNumber_tests() {
        assertTrue(FilterEngine.isValidPhoneNumber("+1234567890"))
        assertTrue(FilterEngine.isValidPhoneNumber("+919876543210"))
        assertTrue(FilterEngine.isValidPhoneNumber("9876543210"))
        assertFalse(FilterEngine.isValidPhoneNumber("123"))
        assertFalse(FilterEngine.isValidPhoneNumber("abc+12345"))
    }
}
