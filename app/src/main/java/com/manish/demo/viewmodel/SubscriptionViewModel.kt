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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.Date

class SubscriptionViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val statsRef = db.collection("Dashboard_stats").document("YbIkiRVdxGQqvza8K85i")

    // 1. Users Stream
    private val usersFlow = callbackFlow<List<Map<String, Any>>> {
        val listener = db.collection("users").addSnapshotListener { snap, _ ->
            trySend(snap?.documents?.map { it.data?.plus("userId" to it.id) ?: emptyMap() }
                ?: emptyList())
        }
        awaitClose { listener.remove() }
    }

    // 2. Plans Stream
    val plansFlow: StateFlow<List<Map<String, Any>>> = callbackFlow<List<Map<String, Any>>> {
        val listener = db.collection("subscription_plans").addSnapshotListener { snap, _ ->
            trySend(snap?.documents?.map { it.data?.plus("planId" to it.id) ?: emptyMap() }
                ?: emptyList())
        }
        awaitClose { listener.remove() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 3. Raw Subscriptions Stream
    private val rawSubsFlow = callbackFlow<List<Map<String, Any>>> {
        val listener = db.collection("subscriptions").addSnapshotListener { snap, _ ->
            trySend(snap?.documents?.map { it.data?.plus("id" to it.id) ?: emptyMap() }
                ?: emptyList())
        }
        awaitClose { listener.remove() }
    }

    // 4. Payments Stream
    private val paymentsFlow = callbackFlow<List<Map<String, Any>>> {
        val listener = db.collection("payments").addSnapshotListener { snap, _ ->
            trySend(snap?.documents?.map { it.data?.plus("paymentId" to it.id) ?: emptyMap() }
                ?: emptyList())
        }
        awaitClose { listener.remove() }
    }

    // 5. THE MASTER JOIN: Fixed syntax from '->' to 'to'
// Inside SubscriptionViewModel.kt

    val subscriptions: StateFlow<List<Map<String, Any>>> =
        combine(rawSubsFlow, usersFlow, plansFlow, paymentsFlow) { subs, users, currentPlans, allPayments ->
            val now = Date()

            subs.map { sub ->
                val userId = sub["userId"].toString()
                val subId = sub["id"].toString()
                val currentPlanId = sub["planId"].toString()
                val endDate = (sub["endDate"] as? Timestamp)?.toDate()
                val dbStatus = sub["status"]?.toString() ?: "active"

                // 1. Get Payment History for this sub
                val history = allPayments.filter { it["subscriptionId"].toString() == subId }
                    .sortedByDescending { (it["createdAt"] as? Timestamp) ?: Timestamp(Date(0)) }

                // 2. Identify Plans (Latest and Previous)
                val latestPayment = history.firstOrNull()
                val secondLatestPayment = if (history.size > 1) history[1] else null

                val currentPlanDoc = currentPlans.find { it["planId"] == currentPlanId }
                val currentPlanName = currentPlanDoc?.get("name")?.toString() ?: "Unknown Plan"

                // 3. Logic for "Plan A + Plan B"
                val displayName = if (secondLatestPayment != null) {
                    val prevPlanId = secondLatestPayment["planId"].toString()
                    val prevPlanDoc = currentPlans.find { it["planId"] == prevPlanId }

                    val prevDuration = (prevPlanDoc?.get("duration") as? Number)?.toInt() ?: 0
                    val prevCreatedAt = (secondLatestPayment["createdAt"] as? Timestamp)?.toDate() ?: Date(0)

                    // Calculate when the previous plan would have expired naturally
                    val prevExpiryCal = Calendar.getInstance().apply {
                        time = prevCreatedAt
                        add(Calendar.DAY_OF_YEAR, prevDuration)
                    }

                    // If naturally not expired yet AND it's a different plan
                    if (prevExpiryCal.time.after(now) && prevPlanId != currentPlanId) {
                        "${prevPlanDoc?.get("name")} + $currentPlanName"
                    } else {
                        currentPlanName
                    }
                } else {
                    currentPlanName
                }

                // Status Logic
                val derivedStatus = when {
                    endDate == null -> dbStatus
                    endDate.before(now) -> "expired"
                    else -> dbStatus
                }

                val user = users.find { it["userId"] == userId || it["uid"] == userId }

                sub + mapOf(
                    "fullName" to (user?.get("fullName") ?: "Unknown User"),
                    "email" to (user?.get("email") ?: "No Email"),
                    "profileImage" to (user?.get("profileImage") ?: ""),
                    "phone" to (user?.get("phone") ?: "No Phone"),
                    "planName" to displayName, // This now shows "A + B"
                    "paymentHistory" to history,
                    "totalAmountPaid" to history.sumOf { (it["amount"] as? Number)?.toDouble() ?: 0.0 },
                    "actualStatus" to derivedStatus
                )
            }.filter { (it["role"]?.toString() ?: "") != "admin" }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 6. Stats logic
    val stats: StateFlow<Map<String, Any>> = combine(subscriptions, paymentsFlow) { subs, payments ->
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 7) }
        val now = Date()

        mapOf(
            "active" to subs.count { it["actualStatus"] == "active" },
            "expired" to subs.count { it["actualStatus"] == "expired" },
            "expiring" to subs.count {
                val endDate = (it["endDate"] as? Timestamp)?.toDate()
                it["actualStatus"] == "active" && endDate != null && endDate.before(cal.time) && endDate.after(now)
            },
            "revenue" to payments.sumOf { (it["amount"] as? Number)?.toDouble() ?: 0.0 }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // Logger
    private fun logActivity(title: String, type: String) {
        db.collection("activities").add(mapOf(
            "title" to title,
            "type" to type,
            "timestamp" to Timestamp.now()
        ))
    }

    // Extension logic
    fun extendSubscription(id: String, userName: String, days: Int) {
        viewModelScope.launch {
            try {
                val doc = db.collection("subscriptions").document(id).get().await()
                val current = doc.getTimestamp("endDate")?.toDate() ?: Date()

                val baseDate = if (current.before(Date())) Date() else current

                val calendar = Calendar.getInstance().apply {
                    time = baseDate
                    add(Calendar.DAY_OF_YEAR, days)
                }
                val newDate = calendar.time

                db.collection("subscriptions").document(id).update(mapOf(
                    "endDate" to Timestamp(newDate),
                    "status" to "active",
                    "updatedAt" to Timestamp.now()
                )).await()

                logActivity("Extended $userName by $days days", "sub")
            } catch (e: Exception) {
                // Log error if needed
            }
        }
    }

    // Plan CRUD
    fun addPlan(n: String, p: Int, d: Int) {
        db.collection("subscription_plans").add(mapOf("name" to n, "price" to p, "duration" to d))
        logActivity("New Plan: $n", "plan")
    }

    fun updatePlan(id: String, n: String, p: Int, d: Int) {
        db.collection("subscription_plans").document(id).update(mapOf("name" to n, "price" to p, "duration" to d))
        logActivity("Updated Plan: $n", "plan")
    }

    fun deletePlan(id: String, name: String) {
        db.collection("subscription_plans").document(id).delete()
        logActivity("Deleted Plan: $name", "plan")
    }

    // Individual payments stream for UI cards
    fun getSubscriptionPayments(subscriptionId: String): StateFlow<List<Map<String, Any>>> = callbackFlow {
        val listener = db.collection("payments")
            .whereEqualTo("subscriptionId", subscriptionId)
            .addSnapshotListener { snap, _ ->
                trySend(snap?.documents?.map { it.data?.plus("paymentId" to it.id) ?: emptyMap() } ?: emptyList())
            }
        awaitClose { listener.remove() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}