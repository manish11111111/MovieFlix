package com.manish.demo.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Date

data class MovieItem(
    val docId: String = "",
    val tmdbId: Int = 0,
    val title: String = "",
    val description: String = "",
    val poster: String = "",
    val backdrop: String = "",
    val streamUrl: String = "",
    val trailerUrl: String = "",
    val genres: List<String> = emptyList(),
    val rating: Double = 0.0,
    val timestamp: Long = 0L
)

data class GenreSection(
    val name: String = "",
    val movies: List<MovieItem> = emptyList()
)

data class UserStats(
    val subscriptionStatus: String = "Inactive",
    val watchCount: Int = 0,
    val favoritesCount: Int = 0,
    val totalMovies: Int = 0
)

class UserHomeViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

    private val _allMovies = MutableStateFlow<List<MovieItem>>(emptyList())
    val allMovies: StateFlow<List<MovieItem>> = _allMovies.asStateFlow()

    private val _topRatedMovies = MutableStateFlow<List<MovieItem>>(emptyList())
    val topRatedMovies: StateFlow<List<MovieItem>> = _topRatedMovies.asStateFlow()

    private val _newlyAddedMovies = MutableStateFlow<List<MovieItem>>(emptyList())
    val newlyAddedMovies: StateFlow<List<MovieItem>> = _newlyAddedMovies.asStateFlow()

    private val _genreSections = MutableStateFlow<List<GenreSection>>(emptyList())
    val genreSections: StateFlow<List<GenreSection>> = _genreSections.asStateFlow()

    private val _searchResults = MutableStateFlow<List<MovieItem>>(emptyList())
    val searchResults: StateFlow<List<MovieItem>> = _searchResults.asStateFlow()

    private val _userStats = MutableStateFlow(UserStats())
    val userStats: StateFlow<UserStats> = _userStats.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        fetchMoviesFromFirebase()
        fetchUserStats()
    }

    private fun fetchMoviesFromFirebase() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                db.collection("movies")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.e("UserHomeVM", "Error fetching movies: ${error.message}")
                            _isLoading.value = false
                            return@addSnapshotListener
                        }

                        val movies = snapshot?.documents?.mapNotNull { doc ->
                            try {
                                MovieItem(
                                    docId = doc.id,
                                    tmdbId = (doc.get("tmdbId") as? Number)?.toInt() ?: 0,
                                    title = doc.getString("title") ?: "",
                                    description = doc.getString("description") ?: "",
                                    poster = doc.getString("poster") ?: "",
                                    backdrop = doc.getString("backdrop") ?: "",
                                    streamUrl = doc.getString("streamUrl") ?: "",
                                    trailerUrl = doc.getString("trailerUrl") ?: "",
                                    genres = (doc.get("genres") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                                    rating = (doc.get("rating") as? Number)?.toDouble() ?: 0.0,
                                    timestamp = (doc.get("timestamp") as? Number)?.toLong() ?: 0L
                                )
                            } catch (e: Exception) {
                                Log.e("UserHomeVM", "Error parsing movie: ${e.message}")
                                null
                            }
                        } ?: emptyList()

                        _allMovies.value = movies

                        // Update total movies count
                        _userStats.value = _userStats.value.copy(totalMovies = movies.size)

                        // Top Rated: Sort by rating (top 20)
                        _topRatedMovies.value = movies.sortedByDescending { it.rating }.take(20)

                        // Newly Added: Already sorted by timestamp DESC (top 20)
                        _newlyAddedMovies.value = movies.take(20)

                        // Group by genres
                        groupMoviesByGenres(movies)

                        _isLoading.value = false
                        Log.d("UserHomeVM", "Loaded ${movies.size} movies")
                    }
            } catch (e: Exception) {
                Log.e("UserHomeVM", "Error: ${e.message}")
                _isLoading.value = false
            }
        }
    }

    private fun fetchUserStats() {
        if (currentUserId == null) return

        viewModelScope.launch {
            try {
                // Fetch subscription status
                db.collection("subscriptions")
                    .whereEqualTo("userId", currentUserId)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.e("UserHomeVM", "Error fetching subscription: ${error.message}")
                            return@addSnapshotListener
                        }

                        val activeSub = snapshot?.documents?.firstOrNull { doc ->
                            val expiryDate = doc.getTimestamp("expiryDate")?.toDate()
                            expiryDate != null && expiryDate.after(Date())
                        }

                        val status = if (activeSub != null) "Active" else "Inactive"
                        _userStats.value = _userStats.value.copy(subscriptionStatus = status)
                        Log.d("UserHomeVM", "Subscription status: $status")
                    }

                // Fetch watch history count
                db.collection("users").document(currentUserId)
                    .collection("watchHistory")
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.e("UserHomeVM", "Error fetching watch history: ${error.message}")
                            return@addSnapshotListener
                        }

                        val count = snapshot?.size() ?: 0
                        _userStats.value = _userStats.value.copy(watchCount = count)
                        Log.d("UserHomeVM", "Watch count: $count")
                    }

                // Fetch favorites count
                db.collection("users").document(currentUserId)
                    .collection("favorites")
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.e("UserHomeVM", "Error fetching favorites: ${error.message}")
                            return@addSnapshotListener
                        }

                        val count = snapshot?.size() ?: 0
                        _userStats.value = _userStats.value.copy(favoritesCount = count)
                        Log.d("UserHomeVM", "Favorites count: $count")
                    }

            } catch (e: Exception) {
                Log.e("UserHomeVM", "Error fetching user stats: ${e.message}")
            }
        }
    }

    private fun groupMoviesByGenres(movies: List<MovieItem>) {
        val genreMap = mutableMapOf<String, MutableList<MovieItem>>()

        movies.forEach { movie ->
            movie.genres.forEach { genre ->
                if (genreMap.containsKey(genre)) {
                    genreMap[genre]?.add(movie)
                } else {
                    genreMap[genre] = mutableListOf(movie)
                }
            }
        }

        val genreList = genreMap.map { (name, movieList) ->
            GenreSection(name = name, movies = movieList)
        }.sortedByDescending { it.movies.size }

        _genreSections.value = genreList
        Log.d("UserHomeVM", "Grouped into ${genreList.size} genres")
    }

    fun searchMovies(query: String) {
        if (query.isEmpty()) {
            _searchResults.value = emptyList()
            return
        }

        // ✅ UPDATED: Search only by title
        val filtered = _allMovies.value.filter { movie ->
            movie.title.contains(query, ignoreCase = true)
        }

        _searchResults.value = filtered
        Log.d("UserHomeVM", "Search '$query' found ${filtered.size} results")
    }

    fun clearSearch() {
        _searchResults.value = emptyList()
    }
}
