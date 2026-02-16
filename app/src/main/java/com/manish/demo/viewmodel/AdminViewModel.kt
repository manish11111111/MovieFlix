package com.manish.demo.viewmodel

import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.manish.demo.models.remote.TmdbMovieDto
import com.manish.demo.network.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AdminViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val API_KEY = "4afb7f0966d309df22cf149c7ee24726"

    private val _searchResults = MutableStateFlow<List<TmdbMovieDto>>(emptyList())
    val searchResults: StateFlow<List<TmdbMovieDto>> = _searchResults

    private val _existingMovies = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val existingMovies: StateFlow<List<Map<String, Any>>> = _existingMovies

    private val _allUsers = MutableStateFlow<List<Map<String, Any>>>(emptyList())
    val allUsers: StateFlow<List<Map<String, Any>>> = _allUsers.asStateFlow()

    private val _userRole = MutableStateFlow<String?>(null)
    val userRole: StateFlow<String?> = _userRole.asStateFlow()

    var isLoading = mutableStateOf(false)
    var selectedMovieDetails = mutableStateOf<TmdbMovieDto?>(null)

    init {
        fetchExistingMovies()
        fetchUsers()
    }

    fun searchMovies(query: String) {
        if (query.isEmpty()) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            try {
                val response = RetrofitClient.instance.searchMovies(API_KEY, query)
                _searchResults.value = response.results
            } catch (e: Exception) {
                Log.e("AdminVM", "Search Error: ${e.message}")
            }
        }
    }

    fun uploadMovie(
        tmdbMovie: TmdbMovieDto,
        streamUrl: String,
        tmdbGenres: List<String>,
        onComplete: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            isLoading.value = true
            try {
                val videoRes = RetrofitClient.instance.getMovieVideos(tmdbMovie.id, API_KEY)
                val trailerKey = videoRes.results.find {
                    it.site == "YouTube" && it.type == "Trailer"
                }?.key ?: ""

                val movieMap = hashMapOf(
                    "tmdbId" to tmdbMovie.id,
                    "title" to tmdbMovie.title,
                    "description" to tmdbMovie.overview,
                    "poster" to tmdbMovie.fullPosterUrl,
                    "backdrop" to "https://image.tmdb.org/t/p/w780${tmdbMovie.backdropPath}",
                    "streamUrl" to streamUrl,
                    "trailerUrl" to trailerKey, // Fixed syntax
                    "genres" to tmdbGenres,
                    "rating" to tmdbMovie.rating,
                    "timestamp" to System.currentTimeMillis()
                )

                db.collection("movies").add(movieMap).await()
                onComplete(true, "Movie Published!")
            } catch (e: Exception) {
                onComplete(false, "Error: ${e.localizedMessage}")
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
            } catch (e: Exception) { Log.e("AdminVM", "Delete error: ${e.message}") }
        }
    }

    fun fetchFullMovieDetails(movieId: Int) {
        viewModelScope.launch {
            try {
                val details = RetrofitClient.instance.getMovieDetails(movieId, API_KEY)
                selectedMovieDetails.value = details
            } catch (e: Exception) { Log.e("AdminVM", "Details error: ${e.message}") }
        }
    }

    fun fetchUsers() {
        val myUid = FirebaseAuth.getInstance().currentUser?.uid
        db.collection("users").addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
            if (snapshot != null) {
                val list = snapshot.documents.mapNotNull { doc ->
                    val uid = doc.id
                    if (uid == myUid) null
                    else doc.data?.plus("uid" to uid)
                }
                _allUsers.value = list
            }
        }
    }

    // --- TOGGLE ADMIN STATUS (Matches standard "ADMIN" string) ---
    fun toggleAdminStatus(userId: String, isCurrentlyAdmin: Boolean, onComplete: (String) -> Unit) {
        val newRole = if (isCurrentlyAdmin) "USER" else "ADMIN"
        db.collection("users").document(userId)
            .update("role", newRole)
            .addOnSuccessListener {
                onComplete(if (isCurrentlyAdmin) "User demoted" else "User promoted to ADMIN")
            }
            .addOnFailureListener { onComplete("Error: Permission Denied") }
    }

    // --- TOGGLE BAN STATUS ---
    fun toggleBanStatus(userId: String, isCurrentlyBanned: Boolean, onComplete: (String) -> Unit) {
        val newStatus = !isCurrentlyBanned
        db.collection("users").document(userId)
            .update("isBanned", newStatus)
            .addOnSuccessListener {
                onComplete(if (newStatus) "User Banned" else "User Unbanned")
            }
            .addOnFailureListener { onComplete("Error: Permission Denied") }
    }

    fun checkUserRole() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            db.collection("users").document(uid).get()
                .addOnSuccessListener { document ->
                    _userRole.value = document.getString("role")
                }
                .addOnFailureListener { _userRole.value = "error" }
        }
    }

    fun isMovieInLibrary(tmdbId: Int): Boolean {
        return existingMovies.value.any { it["tmdbId"].toString() == tmdbId.toString() }
    }
}