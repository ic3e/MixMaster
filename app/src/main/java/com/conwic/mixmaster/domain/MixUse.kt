package com.conwic.mixmaster.domain

import com.conwic.mixmaster.data.db.entity.UsedAmount

/** One mix that was actually made on this job, with its parts read out of the receipt. */
data class RecordedMix(
    val id: Long,
    val title: String,
    val jobLabel: String,
    val batches: Int,
    val totalGrams: Double,
    val parts: List<UsedAmount>,
    val mixedAt: Long,
)
