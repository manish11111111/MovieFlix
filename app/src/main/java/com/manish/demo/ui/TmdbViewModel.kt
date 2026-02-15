package com.manish.demo.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manish.demo.models.remote.TmdbMovieDto
import com.manish.demo.repository.TmdbRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class TmdbViewModel : ViewModel() {
    private val repository = TmdbRepository()

    private val _searchResults = MutableStateFlow<List<TmdbMovieDto>>(emptyList())
    val searchResults: StateFlow<List<TmdbMovieDto>> = _searchResults

    fun search(query: String) {
        viewModelScope.launch {
            val results = repository.searchMovies(query)
            _searchResults.value = results
        }
    }
}