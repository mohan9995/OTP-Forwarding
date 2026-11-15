package com.otpforwarder.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ForwardStatus {
    FORWARDED,
    BLOCKED,
    FAILED
}

@Entity(tableName = "forwarding_logs")
data class LogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sender: String,
    val recipient: String,
    val originalMessage: String,
    val forwardedMessage: String?,
    val timestamp: Long = System.currentTimeMillis(),
    val status: ForwardStatus,
    val failureReason: String? = null,
    val simSlot: Int? = null
)
