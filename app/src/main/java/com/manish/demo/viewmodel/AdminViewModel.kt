package com.manish.demo.viewmodel

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.manish.demo.models.remote.MovieResponse
import com.manish.demo.models.remote.TmdbMovieDto
import com.manish.demo.network.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import android.util.Log
import kotlinx.coroutines.flow.asStateFlow
import  com.google.firebase.auth.FirebaseAuth
class AdminViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val API_KEY = "4afb7f0966d309df22cf149c7ee24726" // Replace with your real key

    // Search Results from TMDb
    private val _searchResults = MutableStateFlow<List<TmdbMovieDto>>(emptyList())
    val searchResults: StateFlow<List<TmdbMovieDto>> = _searchResults

    // Existing Movies from Firebase
    private val _existingMovies = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val existingMovies: StateFlow<List<Map<String, Any>>> = _existingMovies

    var isLoading = mutableStateOf(false)

    init {
        fetchExistingMovies()
    }

    fun searchMovies(query: String) {
        if (query.isEmpty()) {
            _searchResults.value = emptyList()
            return
        }

        viewModelScope.launch {
            try {
                android.util.Log.d("API_DEBUG", "Calling TMDb with Key: ${API_KEY.take(4)}***")
                val response = RetrofitClient.instance.searchMovies(API_KEY, query)

                android.util.Log.d("API_DEBUG", "Got Response! Size: ${response.results.size}")
                _searchResults.value = response.results

            } catch (e: Exception) {
                android.util.Log.e("API_DEBUG", "API Error: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    // Update your uploadMovie function to accept a List of strings
    fun uploadMovie(
        tmdbMovie: TmdbMovieDto,
        streamUrl: String,
        tmdbGenres: List<String>,
        onComplete: (Boolean, String) -> Unit // Updated to pass back status and message
    ) {
        viewModelScope.launch {
            isLoading.value = true
            try {
                // 1. Fetch Trailer
                val videoRes = RetrofitClient.instance.getMovieVideos(tmdbMovie.id, API_KEY)
                val trailerKey = videoRes.results.find {
                    it.site == "YouTube" && it.type == "Trailer"
                }?.key ?: ""

                // 2. Prepare Map
                val movieMap = hashMapOf(
                    "tmdbId" to tmdbMovie.id,
                    "title" to tmdbMovie.title,
                    "description" to tmdbMovie.overview,
                    "poster" to tmdbMovie.fullPosterUrl,
                    "backdrop" to "https://image.tmdb.org/t/p/w780${tmdbMovie.backdropPath}",
                    "streamUrl" to streamUrl,
                    "trailerUrl" to "https://www.youtube.com/watch?v=$trailerKey",
                    "genres" to tmdbGenres,
                    "rating" to tmdbMovie.rating,
                    "timestamp" to System.currentTimeMillis()
                )

                // 3. Save to Firebase
                db.collection("movies").add(movieMap).await()

                // 4. TRIGGER SUCCESS CALLBACK
                onComplete(true, "Movie '${tmdbMovie.title}' published successfully!")

            } catch (e: Exception) {
                e.printStackTrace()
                // 5. TRIGGER FAILURE CALLBACK (So the loader stops!)
                onComplete(false, "Failed to publish: ${e.localizedMessage ?: "Unknown Error"}")
            } finally {
                isLoading.value = false
            }
        }
    }

    private fun fetchExistingMovies() {
        db.collection("movies")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                val list = snapshot?.documents?.map { doc ->
                    val data = doc.data ?: mutableMapOf()
                    data["docId"] = doc.id
                    data
                } ?: emptyList()
                _existingMovies.value = list
            }
    }

    fun deleteMovie(docId: String) {
        viewModelScope.launch {
            try {
                db.collection("movies").document(docId).delete().await()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }
    var selectedMovieDetails = mutableStateOf<TmdbMovieDto?>(null)

    fun fetchFullMovieDetails(movieId: Int) {
        viewModelScope.launch {
            try {
                // Now 'details' will be a single movie object containing the genres list
                val details = RetrofitClient.instance.getMovieDetails(movieId, API_KEY)
                selectedMovieDetails.value = details
            } catch (e: Exception) {
                android.util.Log.e("API_ERROR", "Failed to fetch details: ${e.message}")
            }
        }
    }
    // Add this helper function to your AdminViewModel
    fun isMovieInLibrary(tmdbId: Int): Boolean {
        // We check the 'existingMovies' list that we are already fetching from Firebase
        return existingMovies.value.any { it["tmdbId"].toString() == tmdbId.toString() }
    }
    // Inside AdminViewModel.kt
    private val _allUsers = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val allUsers: StateFlow<List<Map<String, Any>>> = _allUsers.asStateFlow()

    fun fetchUsers() {
        val myUid = FirebaseAuth.getInstance().currentUser?.uid

        db.collection("users").addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("AdminVM", "Error: ${error.message}")
                return@addSnapshotListener
            }

            if (snapshot != null) {
                val list = snapshot.documents.mapNotNull { doc ->
                    val uid = doc.id
                    // Hide current admin from the list for safety
                    if (uid == myUid) {
                        null
                    } else {
                        doc.data?.plus("uid" to uid)
                    }
                }
                _allUsers.value = list
            }
        }
    }
    // --- TOGGLE ADMIN STATUS ---
    fun toggleAdminStatus(userId: String, isCurrentlyAdmin: Boolean, onComplete: (String) -> Unit) {
        val newRole = if (isCurrentlyAdmin) "USER" else "admin"
        db.collection("users").document(userId)
            .update("role", newRole)
            .addOnSuccessListener {
                onComplete(if (isCurrentlyAdmin) "User demoted to regular user" else "User promoted to Admin")
            }
            .addOnFailureListener { onComplete("Error: Could not update role") }
    }

    fun toggleBanStatus(userId: String, isCurrentlyBanned: Boolean, onComplete: (String) -> Unit) {
        val newStatus = !isCurrentlyBanned
        db.collection("users").document(userId)
            .update("isBanned", newStatus)
            .addOnSuccessListener {
                onComplete(if (newStatus) "User banned successfully" else "User unbanned successfully")
            }
            .addOnFailureListener { onComplete("Error: Could not update ban status") }
    }
    // Inside AdminViewModel.kt (or create an AuthViewModel)
    private val _userRole = MutableStateFlow<String?>(null)
    val userRole: StateFlow<String?> = _userRole.asStateFlow()

    fun checkUserRole() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            FirebaseFirestore.getInstance().collection("users").document(uid)
                .get()
                .addOnSuccessListener { document ->
                    val role = document.getString("role") // Matches your "admin" or "user" logic
                    _userRole.value = role
                }
                .addOnFailureListener {
                    _userRole.value = "error"
                }
        } else {
            _userRole.value = "unauthenticated"
        }
    }
}