package com.manish.demo.ui.admin

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.manish.demo.ui.components.CustomToastCompose
import com.manish.demo.utils.getResponsiveSizes
import com.manish.demo.viewmodel.SubscriptionViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val emeraldGreen = Color(0xFF2ECC71)
private val glassBg = Color.White.copy(alpha = 0.05f)
private val glassBorder = Color.White.copy(alpha = 0.2f)

@Composable
fun SubscriptionManagementScreen(viewModel: SubscriptionViewModel, modifier: Modifier = Modifier) {
    val subscriptions by viewModel.subscriptions.collectAsState()
    val plans by viewModel.plansFlow.collectAsState()
    val stats by viewModel.stats.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }

    // Dialog control states
    var showAddEditPlan by remember { mutableStateOf<Map<String, Any>?>(null) }
    var showExtendSub by remember { mutableStateOf<Map<String, Any>?>(null) }
    var showDeletePlanDialog by remember { mutableStateOf<Pair<String, String>?>(null) }

    // GLOBAL STATES for Toast and Loader
    var isGlobalLoading by remember { mutableStateOf(false) }
    var globalLoadingMessage by remember { mutableStateOf("") }
    var showToast by remember { mutableStateOf(false) }
    var toastMessage by remember { mutableStateOf("") }

    // Process subscriptions with dynamic status based on end date
    val processedSubscriptions = remember(subscriptions) {
        subscriptions.map { sub ->
            val endDate = (sub["endDate"] as? Timestamp)?.toDate()
            val dbStatus = sub["status"]?.toString() ?: "unknown"

            // Determine actual status based on end date
            val actualStatus = when {
                endDate == null -> dbStatus
                endDate.before(Date()) -> "expired" // End date is in the past
                else -> dbStatus // Keep original status if not expired
            }

            // Create a new map with the actual status
            sub.toMutableMap().apply {
                put("actualStatus", actualStatus)
                put("displayStatus", actualStatus)
            }
        }
    }

    // Calculate stats with dynamic statuses
    val calculatedCounts = remember(processedSubscriptions) {
        val now = Date()
        val active = processedSubscriptions.count {
            it["actualStatus"] == "active"
        }
        val expiring = processedSubscriptions.count { sub ->
            val actualStatus = sub["actualStatus"] as? String ?: ""
            val endDate = (sub["endDate"] as? Timestamp)?.toDate()
            actualStatus == "active" && endDate != null && isExpiringSoon(endDate)
        }
        val expired = processedSubscriptions.count {
            it["actualStatus"] == "expired"
        }

        mapOf(
            "active" to active,
            "expiring" to expiring,
            "expired" to expired
        )
    }

    // Filter subscriptions based on dynamic status
    val filteredSubs = remember(processedSubscriptions, selectedTab, searchQuery) {
        processedSubscriptions.filter { sub ->
            val matchesSearch = listOf("fullName", "email", "phone", "planName").any {
                sub[it]?.toString()?.contains(searchQuery, ignoreCase = true) == true
            }

            val actualStatus = sub["actualStatus"] as? String ?: ""
            val endDate = (sub["endDate"] as? Timestamp)?.toDate()

            val matchesTab = when (selectedTab) {
                0 -> actualStatus == "active" // Active
                1 -> actualStatus == "active" && endDate != null && isExpiringSoon(endDate) // Expiring
                2 -> actualStatus == "expired" // Expired
                3 -> false // Plans tab - handled separately
                else -> true
            }
            matchesSearch && matchesTab
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(modifier = modifier, containerColor = Color.Transparent) { pad ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = pad.calculateBottomPadding())
            ) {

                // Stat Boxes Row - Active, Expiring, Expired, Revenue
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, top = 0.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    StatBox(
                        Modifier.weight(1f),
                        "Active",
                        calculatedCounts["active"].toString(),
                        Icons.Filled.VerifiedUser,
                        emeraldGreen
                    )
                    StatBox(
                        Modifier.weight(1f),
                        "Expiring",
                        calculatedCounts["expiring"].toString(),
                        Icons.Filled.HourglassTop,
                        Color(0xFFFF9800)
                    )
                    StatBox(
                        Modifier.weight(1f),
                        "Expired",
                        calculatedCounts["expired"].toString(),
                        Icons.Filled.Block,
                        Color.Red
                    )
                    val rev = stats["revenue"]?.toString()?.toDoubleOrNull() ?: 0.0
                    StatBox(
                        Modifier.weight(1f),
                        "Revenue",
                        if (rev >= 1000) "₹${String.format("%.2f", rev / 1000)}K"
                        else "₹${String.format("%.2f", rev)}",
                        Icons.Filled.AttachMoney,
                        Color.Yellow
                    )
                }

                // Search Bar
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = glassBg,
                    border = BorderStroke(1.dp, glassBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Search, null, Modifier.size(18.dp), emeraldGreen)
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                            cursorBrush = SolidColor(emeraldGreen),
                            decorationBox = { inner ->
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        "Search subscribers or plans...",
                                        color = Color.White.copy(0.3f),
                                        fontSize = 14.sp
                                    )
                                }
                                inner()
                            }
                        )
                    }
                }

                // Tabs - Active, Expiring, Expired, Plans
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = Color.White,
                    divider = {}
                ) {
                    val tabTitles = listOf("Active", "Expiring", "Expired", "Plans")
                    tabTitles.forEachIndexed { i, t ->
                        val count = when (i) {
                            0 -> calculatedCounts["active"] ?: 0
                            1 -> calculatedCounts["expiring"] ?: 0
                            2 -> calculatedCounts["expired"] ?: 0
                            else -> plans.size
                        }
                        Tab(
                            selected = selectedTab == i,
                            onClick = { selectedTab = i },
                            text = {
                                Text(
                                    "$t (${count})",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        )
                    }
                }

                // Content area - takes remaining space with weight(1f)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    if (selectedTab == 3) {
                        PlanContent(
                            plans = plans,
                            onAdd = { showAddEditPlan = emptyMap() },
                            onEdit = { showAddEditPlan = it },
                            onDelete = { planId, planName ->
                                showDeletePlanDialog = Pair(planId, planName)
                            },
                            isGlobalLoading = isGlobalLoading
                        )
                    } else {
                        SubscriptionContent(
                            list = filteredSubs,
                            onExtend = { showExtendSub = it },
                            isGlobalLoading = isGlobalLoading
                        )
                    }
                }
            }
        }

        // Loader Overlay
        AnimatedVisibility(visible = isGlobalLoading, enter = fadeIn(), exit = fadeOut()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .zIndex(10f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = emeraldGreen)
                    Spacer(Modifier.height(16.dp))
                    Text(globalLoadingMessage, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Toast Overlay
        CustomToastCompose(
            message = toastMessage,
            showToast = showToast,
            onDismiss = { showToast = false }
        )
    }

    // Dialogs
    showAddEditPlan?.let {
        PlanDialog(
            plan = it,
            onDismiss = { showAddEditPlan = null },
            vm = viewModel,
            onLoading = { msg, loading -> isGlobalLoading = loading; globalLoadingMessage = msg },
            onSuccess = { msg -> toastMessage = msg; showToast = true }
        )
    }

    showExtendSub?.let {
        ExtendSubDialog(
            sub = it,
            onDismiss = { showExtendSub = null },
            vm = viewModel,
            onLoading = { msg, loading -> isGlobalLoading = loading; globalLoadingMessage = msg },
            onSuccess = { msg -> toastMessage = msg; showToast = true }
        )
    }

    showDeletePlanDialog?.let { (planId, planName) ->
        DeletePlanDialog(
            planId = planId,
            planName = planName,
            onDismiss = { showDeletePlanDialog = null },
            vm = viewModel,
            onLoading = { msg, loading -> isGlobalLoading = loading; globalLoadingMessage = msg },
            onSuccess = { msg -> toastMessage = msg; showToast = true }
        )
    }
}

// Helper function to check if a date is expiring within 7 days
fun isExpiringSoon(date: Date): Boolean {
    val now = Date()
    val sevenDaysFromNow = Calendar.getInstance().apply {
        time = now
        add(Calendar.DAY_OF_YEAR, 7)
    }.time
    return date in now..sevenDaysFromNow
}

@Composable
private fun SubscriptionContent(
    list: List<Map<String, Any>>,
    onExtend: (Map<String, Any>) -> Unit,
    isGlobalLoading: Boolean
) {
    if (list.isEmpty()) {
        // Show empty state
        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.Search,
                    null,
                    Modifier.size(48.dp),
                    Color.White.copy(0.3f)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "No subscriptions found",
                    color = Color.White.copy(0.5f),
                    fontSize = 16.sp
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(list, key = { it["id"].toString() }) { sub ->
                // Use actualStatus for display
                val status = sub["actualStatus"] as? String ?: sub["status"]?.toString() ?: "unknown"
                val isExpired = status == "expired"
                val isActive = status == "active"
                val isExpiring = status == "active" && isExpiringSoon((sub["endDate"] as? Timestamp)?.toDate() ?: Date())

                val date = (sub["endDate"] as? Timestamp)?.toDate()
                val photoBase64 = sub["profileImage"]?.toString() ?: ""
                val imgSrc =
                    if (photoBase64.isEmpty()) null else if (photoBase64.contains(",")) photoBase64.split(
                        ","
                    )[1] else photoBase64

                val decodedBitmap = remember(imgSrc) {
                    if (imgSrc != null) {
                        try {
                            val bytes = Base64.decode(imgSrc, Base64.DEFAULT)
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                        } catch (e: Exception) {
                            null
                        }
                    } else null
                }

                // Get stored price from subscription
                val planPrice = sub["planPrice"] as? Number ?: 0
                val paymentMethod = sub["paymentMethod"]?.toString() ?: "Unknown"

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = glassBg),
                    border = BorderStroke(1.dp, if (isExpired) Color.Red.copy(0.3f) else if (isExpiring) Color(0xFFFF9800).copy(0.3f) else glassBorder)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (decodedBitmap != null) {
                                Image(
                                    bitmap = decodedBitmap,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(45.dp)
                                        .clip(CircleShape)
                                        .border(1.dp, emeraldGreen, CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(45.dp)
                                        .background(Color.Gray.copy(0.3f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = sub["fullName"].toString().take(1).uppercase(),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = sub["fullName"].toString(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.Email,
                                        null,
                                        Modifier.size(10.dp),
                                        Color.White.copy(0.4f)
                                    )
                                    Text(
                                        " EMAIL: ",
                                        color = Color.White.copy(0.5f),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        sub["email"].toString(),
                                        color = Color.White.copy(0.7f),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            StatusPill(status = status)
                        }

                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.Phone,
                                        null,
                                        Modifier.size(10.dp),
                                        Color.White.copy(0.4f)
                                    )
                                    Text(
                                        " PHONE ",
                                        color = Color.White.copy(0.5f),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    sub["phone"].toString(),
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(start = 14.dp)
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        if (isExpired) "EXPIRED " else "EXPIRES ",
                                        color = if (isExpired) Color.Red.copy(0.7f) else if (isExpiring) Color(0xFFFF9800).copy(0.7f) else Color.White.copy(0.5f),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Icon(
                                        Icons.Filled.CalendarMonth,
                                        null,
                                        Modifier.size(10.dp),
                                        Color.White.copy(0.4f)
                                    )
                                }
                                Text(
                                    date?.let {
                                        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(it)
                                    } ?: "N/A",
                                    color = if (isExpired) Color.Red else if (isExpiring) Color(0xFFFF9800) else Color.White,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(end = 14.dp)
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 10.dp),
                            color = Color.White.copy(0.05f)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Star, null, Modifier.size(12.dp), emeraldGreen)
                                    Text(
                                        " PLAN: ",
                                        color = Color.White.copy(0.5f),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        sub["planName"].toString(),
                                        color = Color.White,
                                        fontSize = 12.sp
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 2.dp)
                                ) {
                                    Icon(Icons.Filled.Payment, null, Modifier.size(12.dp), Color.Yellow)
                                    Text(
                                        " PAY: ",
                                        color = Color.White.copy(0.5f),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        paymentMethod,
                                        color = Color.White.copy(0.8f),
                                        fontSize = 11.sp
                                    )
                                    // Show stored price
                                    if (planPrice.toInt() > 0) {
                                        Text(
                                            " • ₹$planPrice",
                                            color = emeraldGreen,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // Show extend button for ALL subscriptions (active, expiring, and expired)
                            IconButton(
                                onClick = { if (!isGlobalLoading) onExtend(sub) },
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(
                                        if (isExpired) Color.Red.copy(0.2f)
                                        else if (isExpiring) Color(0xFFFF9800).copy(0.2f)
                                        else emeraldGreen.copy(0.2f),
                                        CircleShape
                                    ),
                                enabled = !isGlobalLoading
                            ) {
                                Icon(
                                    Icons.Filled.AddCircle,
                                    null,
                                    Modifier.size(16.dp),
                                    if (isExpired) Color.Red
                                    else if (isExpiring) Color(0xFFFF9800)
                                    else emeraldGreen
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanDialog(
    plan: Map<String, Any>,
    onDismiss: () -> Unit,
    vm: SubscriptionViewModel,
    onLoading: (String, Boolean) -> Unit,
    onSuccess: (String) -> Unit
) {
    var name by remember { mutableStateOf(plan["name"]?.toString() ?: "") }
    var price by remember { mutableStateOf(plan["price"]?.toString() ?: "") }
    var days by remember { mutableStateOf(plan["duration"]?.toString() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1C1E),
        title = { Text(if (plan.isEmpty()) "Add Plan" else "Edit Plan", color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = emeraldGreen
                    )
                )
                OutlinedTextField(
                    value = price,
                    onValueChange = { price = it },
                    label = { Text("Price") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = emeraldGreen
                    )
                )
                OutlinedTextField(
                    value = days,
                    onValueChange = { days = it },
                    label = { Text("Days") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = emeraldGreen
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onLoading("Saving plan...", true)
                    if (plan.isEmpty()) {
                        vm.addPlan(name, price.toIntOrNull() ?: 0, days.toIntOrNull() ?: 0)
                        onSuccess("Plan added successfully")
                    } else {
                        vm.updatePlan(
                            plan["planId"].toString(),
                            name,
                            price.toIntOrNull() ?: 0,
                            days.toIntOrNull() ?: 0
                        )
                        onSuccess("Plan updated successfully")
                    }
                    onLoading("", false)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
            ) {
                Text("Save", color = emeraldGreen, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White.copy(0.7f))
            }
        }
    )
}

@Composable
private fun DeletePlanDialog(
    planId: String,
    planName: String,
    onDismiss: () -> Unit,
    vm: SubscriptionViewModel,
    onLoading: (String, Boolean) -> Unit,
    onSuccess: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1C1E),
        title = { Text("Delete Plan", color = Color.White) },
        text = {
            Text(
                "Are you sure you want to delete \"$planName\"? This action cannot be undone.",
                color = Color.White.copy(0.7f)
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onLoading("Deleting plan...", true)
                    vm.deletePlan(planId, planName)
                    onSuccess("Plan deleted successfully")
                    onLoading("", false)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
            ) {
                Text("Delete", color = Color.Red, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White.copy(0.7f))
            }
        }
    )
}

@Composable
private fun ExtendSubDialog(
    sub: Map<String, Any>,
    onDismiss: () -> Unit,
    vm: SubscriptionViewModel,
    onLoading: (String, Boolean) -> Unit,
    onSuccess: (String) -> Unit
) {
    var days by remember { mutableStateOf("30") }
    val userName = sub["fullName"].toString()
    val currentStatus = sub["actualStatus"] as? String ?: sub["status"]?.toString() ?: "unknown"
    val isExpired = currentStatus == "expired"

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1C1E),
        title = {
            Text(
                if (isExpired) "Renew Subscription" else "Extend Validity",
                color = Color.White
            )
        },
        text = {
            Column {
                if (isExpired) {
                    Text(
                        "This subscription has expired.",
                        color = Color.White.copy(0.7f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                Text(
                    "Plan: ${sub["planName"]}",
                    color = Color.White.copy(0.7f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = days,
                    onValueChange = { days = it.filter { c -> c.isDigit() } },
                    label = { Text("Additional Days") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = emeraldGreen
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onLoading("Processing...", true)
                    // FIX: Match your ViewModel's function signature
                    vm.extendSubscription(
                        id = sub["id"].toString(),  // parameter name is "id"
                        userName = userName,
                        days = days.toIntOrNull() ?: 0  // parameter name is "days"
                    )
                    onSuccess("Extended for $userName by ${days} days")
                    onLoading("", false)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
            ) {
                Text(
                    if (isExpired) "Renew" else "Extend",
                    color = emeraldGreen,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White.copy(0.7f))
            }
        }
    )
}

@Composable
private fun StatBox(
    modifier: Modifier,
    title: String,
    value: String,
    icon: ImageVector,
    color: Color
) {
    val sizes = getResponsiveSizes()
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = glassBg),
        border = BorderStroke(1.dp, glassBorder),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = sizes.paddingSmall + 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, Modifier.size(sizes.iconSmall), color.copy(alpha = 0.8f))
            Text(value, color = color, fontSize = sizes.subtitleSize, fontWeight = FontWeight.Bold)
            Text(
                title.uppercase(),
                color = Color.White.copy(alpha = 0.5f),
                fontSize = sizes.smallSize,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun StatusPill(status: String) {
    val (backgroundColor, textColor, label) = when (status.lowercase()) {
        "active" -> Triple(emeraldGreen.copy(0.15f), emeraldGreen, "ACTIVE")
        "expired" -> Triple(Color.Red.copy(0.15f), Color.Red, "EXPIRED")
        else -> Triple(Color.Gray.copy(0.15f), Color.Gray, status.uppercase())
    }

    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, textColor.copy(0.4f))
    ) {
        Text(
            label,
            color = textColor,
            fontSize = 8.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun PlanContent(
    plans: List<Map<String, Any>>,
    onAdd: () -> Unit,
    onEdit: (Map<String, Any>) -> Unit,
    onDelete: (String, String) -> Unit,
    isGlobalLoading: Boolean
) {
    val sizes = getResponsiveSizes()

    if (plans.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.Label,
                    null,
                    Modifier.size(48.dp),
                    Color.White.copy(0.3f)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "No plans available",
                    color = Color.White.copy(0.5f),
                    fontSize = 16.sp
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onAdd,
                    colors = ButtonDefaults.buttonColors(containerColor = glassBg),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isGlobalLoading
                ) {
                    Icon(Icons.Default.Add, null, tint = emeraldGreen)
                    Text(
                        " ADD NEW PLAN",
                        color = emeraldGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = sizes.bodySize
                    )
                }
            }
        }
    } else {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Button(
                onClick = onAdd,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sizes.paddingLarge * 2),
                colors = ButtonDefaults.buttonColors(containerColor = glassBg),
                shape = RoundedCornerShape(12.dp),
                enabled = !isGlobalLoading
            ) {
                Icon(Icons.Default.Add, null, tint = emeraldGreen)
                Text(
                    " ADD NEW PLAN",
                    color = emeraldGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = sizes.bodySize
                )
            }

            Spacer(Modifier.height(sizes.paddingMedium))

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(plans) { plan ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = glassBg),
                        border = BorderStroke(1.dp, glassBorder),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(sizes.paddingMedium),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(sizes.paddingTiny)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Label,
                                        null,
                                        Modifier.size(sizes.iconSmall),
                                        emeraldGreen
                                    )
                                    Spacer(Modifier.width(sizes.paddingSmall))
                                    Text(
                                        text = plan["name"].toString(),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = sizes.subtitleSize
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Timer,
                                        null,
                                        Modifier.size(sizes.iconSmall - 2.dp),
                                        Color.White.copy(0.5f)
                                    )
                                    Spacer(Modifier.width(sizes.paddingSmall))
                                    Text(
                                        text = "${plan["duration"]} Days Access",
                                        color = Color.White.copy(0.6f),
                                        fontSize = sizes.captionSize
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Payments,
                                        null,
                                        Modifier.size(sizes.iconSmall - 2.dp),
                                        Color.Yellow.copy(0.8f)
                                    )
                                    Spacer(Modifier.width(sizes.paddingSmall))
                                    Text(
                                        text = "₹${plan["price"]}",
                                        color = emeraldGreen,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = sizes.titleSize
                                    )
                                }
                            }
                            // Actions
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(sizes.paddingSmall)
                            ) {
                                IconButton(
                                    onClick = { if (!isGlobalLoading) onEdit(plan) },
                                    modifier = Modifier
                                        .size(sizes.iconMedium + 12.dp)
                                        .background(Color.White.copy(0.05f), CircleShape),
                                    enabled = !isGlobalLoading
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        null,
                                        Modifier.size(sizes.iconSmall + 2.dp),
                                        Color.White.copy(0.7f)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        if (!isGlobalLoading) onDelete(
                                            plan["planId"].toString(),
                                            plan["name"].toString()
                                        )
                                    },
                                    modifier = Modifier
                                        .size(sizes.iconMedium + 12.dp)
                                        .background(Color.Red.copy(0.1f), CircleShape),
                                    enabled = !isGlobalLoading
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        null,
                                        Modifier.size(sizes.iconSmall + 2.dp),
                                        Color.Red.copy(0.8f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}