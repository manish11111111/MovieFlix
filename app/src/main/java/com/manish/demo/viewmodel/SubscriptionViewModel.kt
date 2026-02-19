package com.manish.demo.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.Date

class SubscriptionViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val statsRef = db.collection("Dashboard_stats").document("YbIkiRVdxGQqvza8K85i")

    // 1. Users Stream
    private val usersFlow = callbackFlow {
        val listener = db.collection("users").addSnapshotListener { snap, _ ->
            trySend(snap?.documents?.map { it.data?.plus("userId" to it.id) ?: emptyMap() }
                ?: emptyList())
        }
        awaitClose { listener.remove() }
    }

    // 2. Plans Stream
    val plansFlow: StateFlow<List<Map<String, Any>>> = callbackFlow {
        val listener = db.collection("subscription_plans").addSnapshotListener { snap, _ ->
            trySend(snap?.documents?.map { it.data?.plus("planId" to it.id) ?: emptyMap() }
                ?: emptyList())
        }
        awaitClose { listener.remove() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 3. Subscriptions Stream
    private val rawSubsFlow = callbackFlow {
        val listener = db.collection("subscriptions").addSnapshotListener { snap, _ ->
            trySend(snap?.documents?.map { it.data?.plus("id" to it.id) ?: emptyMap() }
                ?: emptyList())
        }
        awaitClose { listener.remove() }
    }

    // 4. RELATIONAL JOIN: Combines all collections dynamically
    val subscriptions: StateFlow<List<Map<String, Any>>> =
        combine(rawSubsFlow, usersFlow, plansFlow) { subs, users, currentPlans ->
            subs.map { sub ->
                val userId = sub["userId"].toString()
                val planId = sub["planId"].toString()

                val user = users.find { it["userId"] == userId || it["uid"] == userId }
                val plan = currentPlans.find { it["planId"] == planId }

                sub + mapOf(
                    "fullName" to (user?.get("fullName") ?: "Unknown User"),
                    "email" to (user?.get("email") ?: "No Email"),
                    "profileImage" to (user?.get("profileImage") ?: ""),
                    "phone" to (user?.get("phone") ?: "No Phone"),
                    "role" to (user?.get("role") ?: "user"),
                    "planName" to (plan?.get("name") ?: "Plan Deleted"),
                    "planPrice" to (plan?.get("price") ?: 0)
                )
            }.filter {
                val role = it["role"]?.toString() ?: ""
                !role.equals("admin", true)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val stats: StateFlow<Map<String, Any>> = subscriptions.map { list ->
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 7) }
        mapOf(
            "active" to list.count { it["status"] == "active" },
            "deactivated" to list.count { it["status"] == "deactivated" },
            "expiring" to list.count {
                it["status"] == "active" && (it["endDate"] as? Timestamp)?.toDate()
                    ?.before(cal.time) == true
            },
            "revenue" to list.sumOf { (it["planPrice"] as? Number)?.toDouble() ?: 0.0 }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // --- ACTIVITY LOGGER HELPER ---
    private fun logActivity(title: String, type: String) {
        val activity = hashMapOf(
            "title" to title,
            "type" to type, // "sub", "plan"
            "timestamp" to Timestamp.now()
        )
        db.collection("activities").add(activity)
    }

    // --- CRUD ACTIONS WITH LOGS & METADATA ---

    fun addPlan(n: String, p: Int, d: Int) {
        db.collection("subscription_plans").add(mapOf("name" to n, "price" to p, "duration" to d))
        logActivity("New Plan Created: $n", "plan")
    }

    fun updatePlan(id: String, n: String, p: Int, d: Int) {
        db.collection("subscription_plans").document(id)
            .update(mapOf("name" to n, "price" to p, "duration" to d))
        logActivity("Plan Updated: $n", "plan")
    }

    fun deletePlan(id: String, planName: String) {
        db.collection("subscription_plans").document(id).delete()
        logActivity("Plan Deleted: $planName", "plan")
    }

    fun deactivateSubscription(id: String, userName: String) {
        db.collection("subscriptions").document(id).update("status", "deactivated")

        // Update Metadata & Log
        statsRef.update("activeSubs", FieldValue.increment(-1))
        logActivity("Subscription disabled for $userName", "sub")
    }

    fun activateSubscription(id: String, userName: String) {
        db.collection("subscriptions").document(id).update("status", "active")

        // Update Metadata & Log
        statsRef.update("activeSubs", FieldValue.increment(1))
        logActivity("Subscription restored for $userName", "sub")
    }

    fun extendSubscription(id: String, userName: String, days: Int) {
        viewModelScope.launch {
            val doc = db.collection("subscriptions").document(id).get().await()
            val current = doc.getTimestamp("endDate")?.toDate() ?: Date()
            val newDate = Calendar.getInstance()
                .apply { time = current; add(Calendar.DAY_OF_YEAR, days) }.time

            db.collection("subscriptions").document(id).update(
                mapOf(
                    "endDate" to Timestamp(newDate),
                    "status" to "active"
                )
            )

            logActivity("Validity extended for $userName by $days days", "sub")
        }
    }
}