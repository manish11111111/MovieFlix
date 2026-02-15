package com.manish.demo.models.remote

data class TmdbVideoResponse(
    val results: List<TmdbVideoDto>
)

data class TmdbVideoDto(
    val key: String,     // This is the YouTube ID (e.g., "dQw4w9WgXcQ")
    val site: String,    // We look for "YouTube"
    val type: String     // We look for "Trailer"
)