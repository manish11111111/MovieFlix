package com.manish.demo.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

// 1. MOVIE CONTENT
@Entity(tableName = "movies")
data class MovieEntity(
    @PrimaryKey(autoGenerate = true) val movieId: Long = 0,
    val title: String,
    val description: String,
    val posterUrl: String,      // Image URL
    val videoUrl: String,       // Stream URL
    val trailerUrl: String?,
    val releaseYear: Int,
    val durationMinutes: Int,
    val pgRating: String,       // "PG-13", "R"
    val imdbRating: Double,     // 8.5
    val isPremium: Boolean = false, // If true, user needs subscription
    val views: Long = 0         // To calculate "Popular" movies
)

// 2. GENRES (Lookup Table)
@Entity(tableName = "genres")
data class GenreEntity(
    @PrimaryKey(autoGenerate = true) val genreId: Int = 0,
    val name: String // "Action", "Horror", "Sci-Fi"
)

// 3. LANGUAGES (Lookup Table)
@Entity(tableName = "languages")
data class LanguageEntity(
    @PrimaryKey(autoGenerate = true) val languageId: Int = 0,
    val name: String, // "English", "Nepali"
    val code: String  // "en", "ne"
)