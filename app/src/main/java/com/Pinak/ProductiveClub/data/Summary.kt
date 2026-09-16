package com.Pinak.ProductiveClub.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.ServerTimestamp

data class Summary(
    val text: String = "",
    val photoUrls: List<String> = emptyList(),
    val date: String = "",
    val screenTimeMinutes: Int = 0,
    val completedTasksCount: Int = 0,
    val totalTasksCount: Int = 0,
    @ServerTimestamp val submittedAt: Timestamp? = null
)
