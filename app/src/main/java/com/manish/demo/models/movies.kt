package com.manish.demo.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.PropertyName // Import this

data class Movie(
    @DocumentId
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val trailerUrl: String = "",
    val fullMovieUrl: String = "",
    val subtitleUrl: String = "", // Note: Ensure this is in your saveMovie function too!
    val posterUrl: String = "",
    val duration: Int = 0,
    val genreIds: List<String> = emptyList(),
    val releaseYear: Int = 0,

    // --- FIX HERE ---
    @get:PropertyName("isPremium")
    val isPremium: Boolean = true, // Default is true, but annotation ensures DB "false" overrides it

    val createdAt: Timestamp = Timestamp.now()
) {
    fun createSearchKeywords(): List<String> {
        return (title.split(" ") + description.split(" "))
            .map { it.lowercase().trim() }
            .filter { it.length > 2 }
    }
}