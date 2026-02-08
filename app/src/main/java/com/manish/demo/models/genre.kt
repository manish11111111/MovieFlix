package com.manish.demo.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class Genre(
    @DocumentId
    val id: String = "",
    val name: String = "",
    val color: String = "#6200EE",
    val movieCount: Int = 0,
    val createdAt: Timestamp = Timestamp.now()
)