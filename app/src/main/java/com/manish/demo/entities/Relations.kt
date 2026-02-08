package com.manish.demo.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

// Junction: Movie <-> Genre
@Entity(
    tableName = "movie_genres",
    primaryKeys = ["movieId", "genreId"],
    foreignKeys = [
        ForeignKey(
            entity = MovieEntity::class,
            parentColumns = ["movieId"],
            childColumns = ["movieId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = GenreEntity::class,
            parentColumns = ["genreId"],
            childColumns = ["genreId"]
        )
    ],
    indices = [Index("movieId"), Index("genreId")]
)
data class MovieGenreCrossRef(
    val movieId: Long,
    val genreId: Int
)

// Junction: Movie <-> Language
@Entity(
    tableName = "movie_languages",
    primaryKeys = ["movieId", "languageId"],
    foreignKeys = [
        ForeignKey(
            entity = MovieEntity::class,
            parentColumns = ["movieId"],
            childColumns = ["movieId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = LanguageEntity::class,
            parentColumns = ["languageId"],
            childColumns = ["languageId"]
        )
    ],
    indices = [Index("movieId"), Index("languageId")]
)
data class MovieLanguageCrossRef(
    val movieId: Long,
    val languageId: Int
)