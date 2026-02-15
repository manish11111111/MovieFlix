package com.manish.demo.models.remote

import com.google.gson.annotations.SerializedName

data class MovieResponse(
    val results: List<TmdbMovieDto>
)

data class TmdbMovieDto(
    val id: Int,
    val title: String,
    val overview: String,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("backdrop_path") val backdropPath: String?,
    @SerializedName("vote_average") val rating: Double,

    // 1. For Search results (TMDb sends numbers)
    @SerializedName("genre_ids") val genreIds: List<Int>? = null,

    // 2. For Movie Details (TMDb sends objects) - THIS FIXES YOUR ERROR
    val genres: List<Genre>? = null
) {
    val fullPosterUrl: String get() = "https://image.tmdb.org/t/p/w500$posterPath"
}

// 3. Add this class in the same file
data class Genre(
    val id: Int,
    val name: String
)