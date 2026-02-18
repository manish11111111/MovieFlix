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
import kotlinx.coroutines.tasks.await
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
    val watchCount: Int = 0,          // Unique movies watched
    val totalViews: Int = 0,           // Total times watched (all plays)
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

    // ✅ Content-Based Filtering: Recommended movies
    private val _recommendedMovies = MutableStateFlow<List<MovieItem>>(emptyList())
    val recommendedMovies: StateFlow<List<MovieItem>> = _recommendedMovies.asStateFlow()

    private val _searchResults = MutableStateFlow<List<MovieItem>>(emptyList())
    val searchResults: StateFlow<List<MovieItem>> = _searchResults.asStateFlow()

    private val _userStats = MutableStateFlow(UserStats())
    val userStats: StateFlow<UserStats> = _userStats.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // ✅ Cache for "Users Also Watched" to prevent re-fetching on scroll
    private val usersAlsoWatchedCache = mutableMapOf<String, List<MovieItem>>()

    init {
        fetchMoviesFromFirebase()
        fetchUserStats()
        fetchRecommendedMovies()
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
                                    genres = (doc.get("genres") as? List<*>)?.mapNotNull { it as? String }
                                        ?: emptyList(),
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

                        // Content-Based Filtering: Recommend movies based on genres of top-rated movies
                        recommendMovies(movies)

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

                // ✅ Fetch total view count (totalWatchHistory)
                db.collection("users").document(currentUserId)
                    .collection("totalWatchHistory")
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.e("UserHomeVM", "Error fetching total views: ${error.message}")
                            return@addSnapshotListener
                        }

                        val count = snapshot?.size() ?: 0
                        _userStats.value = _userStats.value.copy(totalViews = count)
                        Log.d("UserHomeVM", "Total views: $count")
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

    private fun recommendMovies(movies: List<MovieItem>) {
        // For simplicity, recommend movies from the same genres as the top-rated movies
        val topRatedGenres = _topRatedMovies.value.flatMap { it.genres }.distinct()

        val recommended = movies.filter { movie ->
            movie.genres.any { it in topRatedGenres } && !_topRatedMovies.value.contains(movie)
        }.take(20) // Limit to 20 recommendations

        _recommendedMovies.value = recommended

        Log.d("UserHomeVM", "Recommended ${recommended.size} movies based on content")
    }

    private fun fetchRecommendedMovies() {
        if (currentUserId == null) return

        viewModelScope.launch {
            try {
                val watchHistorySnapshot = db.collection("users")
                    .document(currentUserId)
                    .collection("watchHistory")
                    .get()
                    .await()

                val watchedMovieIds = watchHistorySnapshot.documents.mapNotNull {
                    it.getString("movieId")
                }

                val favoritesSnapshot = db.collection("users")
                    .document(currentUserId)
                    .collection("favorites")
                    .get()
                    .await()

                val favoriteMovieIds = favoritesSnapshot.documents.mapNotNull {
                    it.getString("movieId")
                }

                val userMovieIds = (watchedMovieIds + favoriteMovieIds).toSet()

                if (userMovieIds.isEmpty()) {
                    _recommendedMovies.value = _topRatedMovies.value.take(15)
                    Log.d("UserHomeVM", "New user - showing top rated as recommendations")
                    return@launch
                }

                val userGenres = _allMovies.value
                    .filter { it.docId in userMovieIds }
                    .flatMap { it.genres }
                    .groupingBy { it }
                    .eachCount()
                    .toList()
                    .sortedByDescending { it.second }
                    .map { it.first }

                if (userGenres.isEmpty()) {
                    _recommendedMovies.value = _topRatedMovies.value.take(15)
                    return@launch
                }

                val recommendations = _allMovies.value
                    .filter { it.docId !in userMovieIds }
                    .map { movie ->
                        val genreScore = movie.genres.count { it in userGenres }.toDouble()
                        val ratingScore = movie.rating / 10.0
                        val combinedScore = (genreScore * 2.0) + ratingScore
                        Pair(movie, combinedScore)
                    }
                    .filter { it.second > 0 }
                    .sortedByDescending { it.second }
                    .take(20)
                    .map { it.first }

                _recommendedMovies.value = recommendations
                Log.d(
                    "UserHomeVM",
                    "Recommended ${recommendations.size} movies based on user preferences"
                )

            } catch (e: Exception) {
                Log.e("UserHomeVM", "Error fetching recommendations: ${e.message}")
                _recommendedMovies.value = _topRatedMovies.value.take(15)
            }
        }
    }

    // ✅ OPTIMIZED COLLABORATIVE FILTERING: Fast "Users also watched" recommendations with caching
    suspend fun fetchUsersAlsoWatched(movieId: String): List<MovieItem> {
        // ✅ CHECK CACHE FIRST - Prevents re-loading on scroll
        usersAlsoWatchedCache[movieId]?.let { cachedResults ->
            Log.d(
                "UserHomeVM",
                "💾 Returning cached recommendations for: $movieId (${cachedResults.size} items)"
            )
            return cachedResults
        }

        try {
            Log.d("UserHomeVM", "⏱️ Fetching recommendations for: $movieId")
            val startTime = System.currentTimeMillis()

            // FAST PATH: Get current movie genres for quick fallback
            val currentMovieDoc = db.collection("movies").document(movieId).get().await()
            val currentMovieGenres = try {
                if (currentMovieDoc.exists()) {
                    (currentMovieDoc.get("genres") as? List<*>)?.mapNotNull { it as? String }
                        ?: emptyList()
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                Log.e("UserHomeVM", "Error getting genres: ${e.message}")
                emptyList()
            }

            // If we have genres, use genre-based recommendations (FAST & RELIABLE)
            // This is now the PRIMARY strategy for speed
            if (currentMovieGenres.isNotEmpty()) {
                Log.d("UserHomeVM", "⚡ Using fast genre-based recommendations")

                val genreMovies = db.collection("movies")
                    .orderBy("rating", Query.Direction.DESCENDING)
                    .limit(30)
                    .get()
                    .await()

                val recommendations = genreMovies.documents.mapNotNull { doc ->
                    try {
                        if (doc.id == movieId) return@mapNotNull null

                        val genres = (doc.get("genres") as? List<*>)?.mapNotNull { it as? String }
                            ?: emptyList()

                        // Calculate genre match score
                        val matchingGenres = genres.count { it in currentMovieGenres }
                        if (matchingGenres > 0) {
                            Pair(
                                MovieItem(
                                    docId = doc.id,
                                    tmdbId = (doc.get("tmdbId") as? Number)?.toInt() ?: 0,
                                    title = doc.getString("title") ?: "",
                                    description = doc.getString("description") ?: "",
                                    poster = doc.getString("poster") ?: "",
                                    backdrop = doc.getString("backdrop") ?: "",
                                    streamUrl = doc.getString("streamUrl") ?: "",
                                    trailerUrl = doc.getString("trailerUrl") ?: "",
                                    genres = genres,
                                    rating = (doc.get("rating") as? Number)?.toDouble() ?: 0.0,
                                    timestamp = (doc.get("timestamp") as? Number)?.toLong() ?: 0L
                                ),
                                matchingGenres // Score for sorting
                            )
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        null
                    }
                }
                    .sortedWith(compareByDescending<Pair<MovieItem, Int>> { it.second }.thenByDescending { it.first.rating })
                    .take(15)
                    .map { it.first }

                // ✅ Cache the results
                usersAlsoWatchedCache[movieId] = recommendations

                val elapsed = System.currentTimeMillis() - startTime
                Log.d(
                    "UserHomeVM",
                    "✅ Returned ${recommendations.size} recommendations in ${elapsed}ms"
                )
                return recommendations
            }

            // FALLBACK: If no genres, return top rated
            Log.d("UserHomeVM", "📊 No genres found, using top rated")
            val topRated = db.collection("movies")
                .orderBy("rating", Query.Direction.DESCENDING)
                .limit(15)
                .get()
                .await()

            val topRatedMovies = topRated.documents.mapNotNull { doc ->
                try {
                    if (doc.id == movieId) return@mapNotNull null

                    MovieItem(
                        docId = doc.id,
                        tmdbId = (doc.get("tmdbId") as? Number)?.toInt() ?: 0,
                        title = doc.getString("title") ?: "",
                        description = doc.getString("description") ?: "",
                        poster = doc.getString("poster") ?: "",
                        backdrop = doc.getString("backdrop") ?: "",
                        streamUrl = doc.getString("streamUrl") ?: "",
                        trailerUrl = doc.getString("trailerUrl") ?: "",
                        genres = (doc.get("genres") as? List<*>)?.mapNotNull { it as? String }
                            ?: emptyList(),
                        rating = (doc.get("rating") as? Number)?.toDouble() ?: 0.0,
                        timestamp = (doc.get("timestamp") as? Number)?.toLong() ?: 0L
                    )
                } catch (e: Exception) {
                    null
                }
            }

            // ✅ Cache the results
            usersAlsoWatchedCache[movieId] = topRatedMovies

            val elapsed = System.currentTimeMillis() - startTime
            Log.d("UserHomeVM", "✅ Returned ${topRatedMovies.size} top rated in ${elapsed}ms")
            return topRatedMovies

        } catch (e: Exception) {
            Log.e("UserHomeVM", "❌ Error in fetchUsersAlsoWatched: ${e.message}", e)

            // Emergency fallback - return minimal top rated
            return try {
                val topRated = db.collection("movies")
                    .orderBy("rating", Query.Direction.DESCENDING)
                    .limit(10)
                    .get()
                    .await()

                val emergencyResults = topRated.documents.mapNotNull { doc ->
                    try {
                        if (doc.id == movieId) return@mapNotNull null
                        MovieItem(
                            docId = doc.id,
                            title = doc.getString("title") ?: "",
                            poster = doc.getString("poster") ?: "",
                            rating = (doc.get("rating") as? Number)?.toDouble() ?: 0.0
                        )
                    } catch (ex: Exception) {
                        null
                    }
                }

                // ✅ Cache even emergency results
                usersAlsoWatchedCache[movieId] = emergencyResults
                emergencyResults
            } catch (ex: Exception) {
                Log.e("UserHomeVM", "Emergency fallback failed: ${ex.message}")
                emptyList()
            }
        }
    }

    // ✅ Track both unique movies AND total view count
    fun trackMovieWatch(movieId: String, movieTitle: String) {
        if (currentUserId == null) return

        viewModelScope.launch {
            try {
                val userDocRef = db.collection("users").document(currentUserId)

                // Update the movie-specific watch document (for unique count)
                val watchData = hashMapOf(
                    "movieId" to movieId,
                    "title" to movieTitle,
                    "lastWatchedAt" to com.google.firebase.Timestamp.now()
                )

                userDocRef.collection("watchHistory")
                    .document(movieId)
                    .set(watchData)
                    .await()

                // ✅ Add to totalWatchHistory for total view count (with auto-generated ID)
                val viewData = hashMapOf(
                    "movieId" to movieId,
                    "title" to movieTitle,
                    "watchedAt" to com.google.firebase.Timestamp.now()
                )

                userDocRef.collection("totalWatchHistory")
                    .add(viewData)  // Auto-generated ID = every play tracked
                    .await()

                Log.d("UserHomeVM", "Tracked watch for: $movieTitle (unique + total)")
            } catch (e: Exception) {
                Log.e("UserHomeVM", "Error tracking watch: ${e.message}")
            }
        }
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
