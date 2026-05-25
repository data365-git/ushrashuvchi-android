package com.example.ui.components

import com.example.data.model.MeetingStatus

/**
 * UI label + visibility rules for meeting status badges and the Generate AI card.
 *
 * Extracted from inline screen logic so the regression suite can pin the labels
 * against the real production source (single point of truth).
 */
object StatusBadgeLabels {
    fun forStatus(status: String): String = when (status) {
        MeetingStatus.RECORDING.name  -> "Recording"
        MeetingStatus.RECORDED.name   -> "Recorded · No AI yet"
        MeetingStatus.PROCESSING.name -> "Processing"
        MeetingStatus.COMPLETED.name  -> "Completed"
        MeetingStatus.FAILED.name     -> "Failed · Retry"
        else                          -> status
    }

    fun shouldShowGenerateCard(status: String): Boolean = status in setOf(
        MeetingStatus.RECORDED.name,
        MeetingStatus.FAILED.name
    )
}
