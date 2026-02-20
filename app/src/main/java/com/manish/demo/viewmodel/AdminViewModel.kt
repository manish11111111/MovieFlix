package com.manish.demo.viewmodel

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Query
import com.manish.demo.models.remote.TmdbMovieDto
import com.manish.demo.network.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    // Add these missing state variables
    var isLoading = mutableStateOf(false)
    var loadingMessage = mutableStateOf("") // Add this line - missing declaration

    var selectedMovieDetails = mutableStateOf<TmdbMovieDto?>(null)
    private val statsRef = db.collection("Dashboard_stats").document("YbIkiRVdxGQqvza8K85i")

    init {
        fetchExistingMovies()
        fetchUsers()
    }

    // --- ACTIVITY LOGGER HELPER ---
    fun logActivity(title: String, type: String) {
        val activity = hashMapOf(
            "title" to title,
            "type" to type,
            "timestamp" to com.google.firebase.Timestamp.now()
        )
        db.collection("activities").add(activity)
    }

    // --- MOVIE ACTIONS ---

    // RESTORED: This was missing in the previous version
    fun fetchFullMovieDetails(movieId: Int) {
        viewModelScope.launch {
            try {
                val details = RetrofitClient.instance.getMovieDetails(movieId, API_KEY)
                selectedMovieDetails.value = details
            } catch (e: Exception) {
                Log.e("AdminVM", "Details error: ${e.message}")
            }
        }
    }

    // RESTORED: This was missing in the previous version
    fun isMovieInLibrary(tmdbId: Int): Boolean {
        return existingMovies.value.any { it["tmdbId"].toString() == tmdbId.toString() }
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
                    "trailerUrl" to trailerKey,
                    "genres" to tmdbGenres,
                    "rating" to tmdbMovie.rating,
                    "timestamp" to System.currentTimeMillis()
                )

                db.collection("movies").add(movieMap).await()

                // UPDATE METADATA & LOG
                statsRef.update("totalMovies", FieldValue.increment(1))
                logActivity("New Movie Added: ${tmdbMovie.title}", "movie")

                onComplete(true, "Movie Published!")
            } catch (e: Exception) {
                onComplete(false, "Error: ${e.localizedMessage}")
            } finally {
                isLoading.value = false
            }
        }
    }

    fun deleteMovie(docId: String, movieTitle: String) {
        viewModelScope.launch {
            try {
                db.collection("movies").document(docId).delete().await()

                // UPDATE METADATA & LOG
                statsRef.update("totalMovies", FieldValue.increment(-1))
                logActivity("Movie Deleted: $movieTitle", "movie")

            } catch (e: Exception) {
                Log.e("AdminVM", "Delete error: ${e.message}")
            }
        }
    }

    // --- USER ACTIONS ---
    fun toggleAdminStatus(
        userId: String,
        userName: String,
        isCurrentlyAdmin: Boolean,
        onComplete: (String) -> Unit
    ) {
        val newRole = if (isCurrentlyAdmin) "USER" else "ADMIN"
        db.collection("users").document(userId)
            .update("role", newRole)
            .addOnSuccessListener {
                val action = if (isCurrentlyAdmin) "demoted to User" else "promoted to Admin"
                logActivity("$userName was $action", "role")
                onComplete("User $action")
            }
    }

    fun toggleBanStatus(
        userId: String,
        userName: String,
        isCurrentlyBanned: Boolean,
        onComplete: (String) -> Unit
    ) {
        val newStatus = !isCurrentlyBanned
        db.collection("users").document(userId)
            .update("isBanned", newStatus)
            .addOnSuccessListener {
                val statusText = if (newStatus) "Banned" else "Unbanned"
                logActivity("User $userName was $statusText", "ban")
                onComplete("User $statusText")
            }
    }

    // --- SEARCH & DATA FETCH ---
    fun searchMovies(query: String) {
        if (query.isEmpty()) {
            _searchResults.value = emptyList(); return
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

    private fun fetchExistingMovies() {
        db.collection("movies").orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                val list = snapshot?.documents?.map { doc ->
                    val data = doc.data ?: mutableMapOf()
                    data["docId"] = doc.id
                    data
                } ?: emptyList()
                _existingMovies.value = list
            }
    }

    fun fetchUsers() {
        val myUid = FirebaseAuth.getInstance().currentUser?.uid
        db.collection("users").addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                val list = snapshot.documents.mapNotNull { doc ->
                    val uid = doc.id
                    if (uid == myUid) null else doc.data?.plus("uid" to uid)
                }
                _allUsers.value = list
            }
        }
    }

    // Inside AdminViewModel class
    fun logPasswordChange() {
        logActivity("Your Password was changed successfully", "security")
    }

    // Add this refreshData function
    fun refreshData() {
        fetchExistingMovies()
        fetchUsers()
    }

    // Add this to your AdminViewModel (for subscription management)
    fun extendSubscription(
        subId: String,
        userName: String,
        additionalDays: Int,
        currentPrice: Int
    ) {
        viewModelScope.launch {
            isLoading.value = true
            loadingMessage.value = "Extending subscription..."

            val db = FirebaseFirestore.getInstance()
            val subRef = db.collection("subscriptions").document(subId)

            db.runTransaction { transaction ->
                val snapshot = transaction.get(subRef)
                val currentEndDate = snapshot.getTimestamp("endDate")?.toDate() ?: Date()

                // Calculate new end date
                val calendar = Calendar.getInstance()
                calendar.time = currentEndDate
                calendar.add(Calendar.DAY_OF_YEAR, additionalDays)
                val newEndDate = calendar.time

                // Update the subscription
                transaction.update(subRef, "endDate", Timestamp(newEndDate))
                transaction.update(subRef, "status", "active")
                transaction.update(subRef, "updatedAt", Timestamp.now())

                // Create a payment record for this extension
                val paymentData = hashMapOf(
                    "subscriptionId" to subId,
                    "userId" to snapshot.getString("userId"),
                    "userName" to userName,
                    "amount" to currentPrice,
                    "type" to "extension",
                    "days" to additionalDays,
                    "paymentMethod" to "Admin Extension",
                    "createdAt" to Timestamp.now()
                )

                // Add to payments collection
                val paymentRef = db.collection("payments").document()
                transaction.set(paymentRef, paymentData)

            }.addOnSuccessListener {
                refreshData()
                isLoading.value = false
                loadingMessage.value = ""
            }.addOnFailureListener { e ->
                isLoading.value = false
                loadingMessage.value = ""
                Log.e("AdminVM", "Extension error: ${e.message}")
            }
        }
    }
}