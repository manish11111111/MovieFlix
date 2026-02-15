package com.manish.demo.repository

import com.manish.demo.models.remote.TmdbMovieDto
import com.manish.demo.network.RetrofitClient

class TmdbRepository {
    private val api = RetrofitClient.instance
    private val API_KEY = "4afb7f0966d309df22cf149c7ee24726" // Replace with your real key

    suspend fun searchMovies(query: String): List<TmdbMovieDto> {
        return try {
            val response = api.searchMovies(API_KEY, query)
            response.results
        } catch (e: Exception) {
            emptyList()
        }
    }
}