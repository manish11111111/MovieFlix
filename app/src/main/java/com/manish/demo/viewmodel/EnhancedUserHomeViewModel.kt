package com.manish.demo.viewmodel

import com.google.firebase.firestore.FieldValue
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
import kotlin.math.sqrt

// User profile for collaborative filtering
data class UserProfile(
    val userId: String = "",
    val genrePreferences: Map<String, Double> = emptyMap(),  // Weighted genre preferences
    val averageRating: Double = 0.0,
    val watchCount: Int = 0
)

// User similarity for collaborative filtering
data class UserSimilarity(
    val userId: String,
    val similarityScore: Double
)

class EnhancedUserHomeViewModel : ViewModel() {
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

    // Content-based recommendations
    private val _contentBasedRecs = MutableStateFlow<List<MovieItem>>(emptyList())
    val contentBasedRecs: StateFlow<List<MovieItem>> = _contentBasedRecs.asStateFlow()

    // Collaborative recommendations
    private val _collaborativeRecs = MutableStateFlow<List<MovieItem>>(emptyList())
    val collaborativeRecs: StateFlow<List<MovieItem>> = _collaborativeRecs.asStateFlow()

    private val _searchResults = MutableStateFlow<List<MovieItem>>(emptyList())
    val searchResults: StateFlow<List<MovieItem>> = _searchResults.asStateFlow()

    private val _userStats = MutableStateFlow(UserStats())
    val userStats: StateFlow<UserStats> = _userStats.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Caches
    private val userProfileCache = mutableMapOf<String, UserProfile>()
    private val userSimilarityCache = mutableMapOf<String, List<UserSimilarity>>()
    private val itemItemSimilarityCache = mutableMapOf<String, List<MovieItem>>()

    init {
        fetchMoviesFromFirebase()
        fetchUserStats()

        viewModelScope.launch {
            if (currentUserId != null) {
                buildUserProfile(currentUserId)
            }
        }
    }

    // ==================== DATA FETCHING ====================

    private fun fetchMoviesFromFirebase() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                db.collection("movies")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.e("EnhancedVM", "Error fetching movies: ${error.message}")
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
                                Log.e("EnhancedVM", "Error parsing movie: ${e.message}")
                                null
                            }
                        } ?: emptyList()

                        _allMovies.value = movies

                        _topRatedMovies.value = movies.sortedByDescending { it.rating }.take(20)
                        _newlyAddedMovies.value = movies.sortedByDescending { it.timestamp }.take(20)

                        groupMoviesByGenres(movies)

                        viewModelScope.launch {
                            if (currentUserId != null) {
                                generateContentBasedRecs(currentUserId)
                            }
                        }

                        _isLoading.value = false
                        Log.d("EnhancedVM", "Loaded ${movies.size} movies")
                    }
            } catch (e: Exception) {
                Log.e("EnhancedVM", "Error: ${e.message}")
                _isLoading.value = false
            }
        }
    }

    // ==================== USER PROFILE BUILDING ====================

    private suspend fun buildUserProfile(userId: String): UserProfile {
        val startTime = System.currentTimeMillis()
        Log.d("EnhancedVM", "Building profile for user: $userId")

        return try {
            // Get watch history
            val watchHistory = db.collection("users")
                .document(userId)
                .collection("watchHistory")
                .get()
                .await()

            val watchedMovieIds = watchHistory.documents.mapNotNull { it.getString("movieId") }

            // Get favorites
            val favorites = db.collection("users")
                .document(userId)
                .collection("favorites")
                .get()
                .await()

            val favoriteMovieIds = favorites.documents.mapNotNull { it.getString("movieId") }

            // Get explicit ratings if you have them
            val ratings = db.collection("users")
                .document(userId)
                .collection("ratings")
                .get()
                .await()

            val ratingMap = ratings.documents.associate { doc ->
                doc.getString("movieId") to (doc.getDouble("rating") ?: 0.0)
            }

            // Combine all user-interacted movies
            val allUserMovieIds = (watchedMovieIds + favoriteMovieIds).toSet()

            if (allUserMovieIds.isEmpty()) {
                // New user - return empty profile
                val profile = UserProfile(userId = userId)
                userProfileCache[userId] = profile
                return profile
            }

            // Get full movie details for these IDs
            val userMovies = _allMovies.value.filter { it.docId in allUserMovieIds }

            // Calculate genre preferences with weights
            val genreWeights = mutableMapOf<String, Double>()

            userMovies.forEach { movie ->
                val weight = when {
                    movie.docId in ratingMap -> ratingMap[movie.docId]!! // Explicit rating
                    movie.docId in favoriteMovieIds -> 1.5 // Favorite bonus
                    else -> 1.0 // Just watched
                }

                movie.genres.forEach { genre ->
                    genreWeights[genre] = genreWeights.getOrDefault(genre, 0.0) + weight
                }
            }

            // Normalize weights
            val maxWeight = genreWeights.values.maxOrNull() ?: 1.0
            val normalizedGenres = if (maxWeight > 0)
                genreWeights.mapValues { it.value / maxWeight }
            else
                genreWeights

            val profile = UserProfile(
                userId = userId,
                genrePreferences = normalizedGenres,
                averageRating = if (ratingMap.isNotEmpty()) ratingMap.values.average() else 0.0,
                watchCount = allUserMovieIds.size
            )

            userProfileCache[userId] = profile

            val elapsed = System.currentTimeMillis() - startTime
            Log.d("EnhancedVM", "Profile built in ${elapsed}ms: ${profile.genrePreferences.size} genres")

            profile
        } catch (e: Exception) {
            Log.e("EnhancedVM", "Error building user profile: ${e.message}")
            UserProfile(userId = userId)
        }
    }

    // ==================== CONTENT-BASED FILTERING ====================

    private suspend fun generateContentBasedRecs(userId: String) {
        viewModelScope.launch {
            try {
                val profile = userProfileCache[userId] ?: buildUserProfile(userId)

                // If user has no history, return popular movies (top rated as fallback)
                if (profile.watchCount == 0) {
                    _contentBasedRecs.value = _topRatedMovies.value.take(20)
                    Log.d("EnhancedVM", "New user - showing top rated as content-based recs")
                    return@launch
                }

                // Get user's watched movies to exclude them
                val watchedMovies = db.collection("users")
                    .document(userId)
                    .collection("watchHistory")
                    .get()
                    .await()
                    .documents
                    .mapNotNull { it.getString("movieId") }
                    .toSet()

                // Score each unwatched movie
                val scoredMovies = _allMovies.value
                    .filter { it.docId !in watchedMovies }
                    .map { movie ->
                        val score = calculateContentScore(movie, profile)
                        Pair(movie, score)
                    }
                    .filter { it.second > 0 }
                    .sortedByDescending { it.second }
                    .take(20)
                    .map { it.first }

                _contentBasedRecs.value = scoredMovies
                Log.d("EnhancedVM", "Content-based: ${scoredMovies.size} recommendations generated")
            } catch (e: Exception) {
                Log.e("EnhancedVM", "Error generating content-based recs: ${e.message}")
                _contentBasedRecs.value = _topRatedMovies.value.take(20)
            }
        }
    }

    private fun calculateContentScore(movie: MovieItem, profile: UserProfile): Double {
        var score = 0.0

        // Genre similarity (weighted by user preferences)
        movie.genres.forEach { genre ->
            score += profile.genrePreferences.getOrDefault(genre, 0.0) * 2.0
        }

        // Boost by movie rating
        score += movie.rating / 10.0

        return score
    }

    // ==================== COLLABORATIVE FILTERING ====================

    private suspend fun findSimilarUsers(userId: String, limit: Int = 10): List<UserSimilarity> {
        // Check cache
        userSimilarityCache[userId]?.let { return it }

        val startTime = System.currentTimeMillis()
        Log.d("EnhancedVM", "Finding similar users for: $userId")

        val currentUserProfile = userProfileCache[userId] ?: buildUserProfile(userId)

        // Get all users who have watch history (simplified approach)
        val allUsers = db.collectionGroup("watchHistory")
            .get()
            .await()
            .documents
            .mapNotNull { it.getString("userId") }
            .distinct()
            .filter { it != userId }
            .take(50) // This .take() is on List, correct!

        if (allUsers.isEmpty()) {
            return emptyList()
        }

        // Build profiles for other users
        val similarities = mutableListOf<UserSimilarity>()

        allUsers.forEach { otherUserId ->
            try {
                val otherProfile = userProfileCache[otherUserId] ?: buildUserProfile(otherUserId)
                if (otherProfile.watchCount > 0) {
                    val similarity = calculateUserSimilarity(currentUserProfile, otherProfile)
                    if (similarity > 0.1) { // Threshold
                        similarities.add(UserSimilarity(otherUserId, similarity))
                    }
                }
            } catch (e: Exception) {
                // Skip this user
            }
        }

        val sortedSimilarities = similarities.sortedByDescending { it.similarityScore }.take(limit)

        val elapsed = System.currentTimeMillis() - startTime
        Log.d("EnhancedVM", "Found ${sortedSimilarities.size} similar users in ${elapsed}ms")

        // Cache the results
        userSimilarityCache[userId] = sortedSimilarities
        return sortedSimilarities
    }

    private fun calculateUserSimilarity(user1: UserProfile, user2: UserProfile): Double {
        // Cosine similarity on genre preferences
        val allGenres = (user1.genrePreferences.keys + user2.genrePreferences.keys).toSet()

        var dotProduct = 0.0
        var norm1 = 0.0
        var norm2 = 0.0

        allGenres.forEach { genre ->
            val v1 = user1.genrePreferences.getOrDefault(genre, 0.0)
            val v2 = user2.genrePreferences.getOrDefault(genre, 0.0)

            dotProduct += v1 * v2
            norm1 += v1 * v1
            norm2 += v2 * v2
        }

        return if (norm1 > 0 && norm2 > 0) {
            dotProduct / (sqrt(norm1) * sqrt(norm2))
        } else {
            0.0
        }
    }

    // ==================== ITEM-ITEM COLLABORATIVE FILTERING ====================

    suspend fun fetchUsersAlsoWatched(movieId: String): List<MovieItem> {
        // Check cache first
        itemItemSimilarityCache[movieId]?.let {
            Log.d("EnhancedVM", "Cache hit for item-based: $movieId")
            return it
        }

        val startTime = System.currentTimeMillis()
        Log.d("EnhancedVM", "Generating item-based recommendations for: $movieId")

        try {
            val currentMovie = _allMovies.value.find { it.docId == movieId } ?: return emptyList()

            // Get users who watched this movie
            val usersWhoWatched = db.collectionGroup("watchHistory")
                .whereEqualTo("movieId", movieId)
                .get()
                .await()
                .documents
                .mapNotNull { it.getString("userId") }
                .distinct()
                .take(30) // This .take() is on List, correct!

            if (usersWhoWatched.isEmpty()) {
                // Fallback to genre-based similarity
                return getGenreBasedSimilar(movieId, currentMovie.genres, 15)
            }

            // Find other movies these users watched
            val otherMovies = mutableMapOf<String, Pair<Int, MovieItem?>>()

            usersWhoWatched.forEach { userId ->
                val userMovies = db.collection("users")
                    .document(userId)
                    .collection("watchHistory")
                    .get()
                    .await()
                    .documents
                    .mapNotNull { doc ->
                        val otherMovieId = doc.getString("movieId")
                        if (otherMovieId != null && otherMovieId != movieId) {
                            otherMovieId to _allMovies.value.find { it.docId == otherMovieId }
                        } else null
                    }

                userMovies.forEach { (otherId, movie) ->
                    if (movie != null) {
                        val currentCount = otherMovies[otherId]?.first ?: 0
                        otherMovies[otherId] = Pair(currentCount + 1, movie)
                    }
                }
            }

            // Sort by how many users watched them and take top results
            val recommendations = otherMovies
                .map { (_, pair) -> pair.second!! }
                .distinctBy { it.docId }
                .take(15) // This .take() is on List, correct!

            val elapsed = System.currentTimeMillis() - startTime
            Log.d("EnhancedVM", "Item-based: ${recommendations.size} recommendations in ${elapsed}ms")

            // Cache results
            itemItemSimilarityCache[movieId] = recommendations
            return recommendations
        } catch (e: Exception) {
            Log.e("EnhancedVM", "Error in item-based: ${e.message}")
            return getGenreBasedSimilar(movieId, emptyList(), 15)
        }
    }

    private fun getGenreBasedSimilar(movieId: String, movieGenres: List<String>, limit: Int): List<MovieItem> {
        if (movieGenres.isEmpty()) {
            return _topRatedMovies.value.take(limit)
        }

        return _allMovies.value
            .filter { it.docId != movieId }
            .map { movie ->
                val commonGenres = movie.genres.count { it in movieGenres }
                Pair(movie, commonGenres)
            }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<MovieItem, Int>> { it.second }
                .thenByDescending { it.first.rating })
            .take(limit)
            .map { it.first }
    }

    // ==================== PUBLIC API METHODS ====================

    fun refreshRecommendations() {
        if (currentUserId != null) {
            viewModelScope.launch {
                // Clear caches
                userSimilarityCache.remove(currentUserId)
                // Rebuild profile and recommendations
                buildUserProfile(currentUserId)
                generateContentBasedRecs(currentUserId)
            }
        }
    }

    // Track movie watch
    fun trackMovieWatch(movieId: String, movieTitle: String, userName: String) {
        if (currentUserId == null) return

        viewModelScope.launch {
            try {
                val userDocRef = db.collection("users").document(currentUserId)

                // Update watch history
                val watchData = hashMapOf(
                    "movieId" to movieId,
                    "title" to movieTitle,
                    "userId" to currentUserId,  // Add userId for collectionGroup queries
                    "lastWatchedAt" to FieldValue.serverTimestamp()
                )

                userDocRef.collection("watchHistory")
                    .document(movieId)
                    .set(watchData)
                    .await()

                // Add to total views
                val viewData = hashMapOf(
                    "movieId" to movieId,
                    "title" to movieTitle,
                    "userId" to currentUserId,
                    "watchedAt" to FieldValue.serverTimestamp()
                )

                userDocRef.collection("totalWatchHistory")
                    .add(viewData)
                    .await()

                Log.d("EnhancedVM", "Tracked watch for: $movieTitle")

                // Refresh recommendations after watch
                refreshRecommendations()

            } catch (e: Exception) {
                Log.e("EnhancedVM", "Error tracking watch: ${e.message}")
            }
        }
    }

    // Rate a movie
    fun rateMovie(movieId: String, rating: Double) {
        if (currentUserId == null) return

        viewModelScope.launch {
            try {
                val ratingData = hashMapOf(
                    "movieId" to movieId,
                    "rating" to rating,
                    "userId" to currentUserId,
                    "timestamp" to FieldValue.serverTimestamp()
                )

                db.collection("users").document(currentUserId)
                    .collection("ratings")
                    .document(movieId)
                    .set(ratingData)
                    .await()

                Log.d("EnhancedVM", "Rated movie: $movieId with $rating")

                // Refresh recommendations after rating
                refreshRecommendations()

            } catch (e: Exception) {
                Log.e("EnhancedVM", "Error rating movie: ${e.message}")
            }
        }
    }

    // Existing helper methods
    private fun groupMoviesByGenres(movies: List<MovieItem>) {
        val genreMap = mutableMapOf<String, MutableList<MovieItem>>()

        movies.forEach { movie ->
            movie.genres.forEach { genre ->
                genreMap.getOrPut(genre) { mutableListOf() }.add(movie)
            }
        }

        val genreList = genreMap.map { (name, movieList) ->
            GenreSection(name = name, movies = movieList)
        }.sortedByDescending { it.movies.size }

        _genreSections.value = genreList
    }

    fun searchMovies(query: String) {
        if (query.isEmpty()) {
            _searchResults.value = emptyList()
            return
        }

        val filtered = _allMovies.value.filter { movie ->
            movie.title.contains(query, ignoreCase = true)
        }

        _searchResults.value = filtered
    }

    fun clearSearch() {
        _searchResults.value = emptyList()
    }

    private fun fetchUserStats() {
        if (currentUserId == null) return

        viewModelScope.launch {
            try {
                // Fetch subscription status
                db.collection("subscriptions")
                    .whereEqualTo("userId", currentUserId)
                    .whereEqualTo("status", "active")
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) return@addSnapshotListener

                        val activeSub = snapshot?.documents?.firstOrNull { doc ->
                            val expiryDate = doc.getTimestamp("endDate")?.toDate()
                            expiryDate != null && expiryDate.after(Date())
                        }

                        val status = if (activeSub != null) "Active" else "Inactive"
                        _userStats.value = _userStats.value.copy(subscriptionStatus = status)
                    }

                // Fetch watch history count
                db.collection("users").document(currentUserId)
                    .collection("watchHistory")
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) return@addSnapshotListener
                        val count = snapshot?.size() ?: 0
                        _userStats.value = _userStats.value.copy(watchCount = count)
                    }

                // Fetch total views
                db.collection("users").document(currentUserId)
                    .collection("totalWatchHistory")
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) return@addSnapshotListener
                        val count = snapshot?.size() ?: 0
                        _userStats.value = _userStats.value.copy(totalViews = count)
                    }

                // Fetch favorites count
                db.collection("users").document(currentUserId)
                    .collection("favorites")
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) return@addSnapshotListener
                        val count = snapshot?.size() ?: 0
                        _userStats.value = _userStats.value.copy(favoritesCount = count)
                    }

            } catch (e: Exception) {
                Log.e("EnhancedVM", "Error fetching user stats: ${e.message}")
            }
        }
    }
}