package com.manish.demo.ui.admin

import com.manish.demo.ui.components.CustomToastCompose
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import androidx.compose.ui.zIndex
import com.google.firebase.Timestamp
import com.manish.demo.viewmodel.SubscriptionViewModel
import java.text.SimpleDateFormat
import java.util.*

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
    var showManageSub by remember { mutableStateOf<Map<String, Any>?>(null) }
    var showExtendSub by remember { mutableStateOf<Map<String, Any>?>(null) }
    var showDeletePlanDialog by remember { mutableStateOf<Pair<String, String>?>(null) }

    // GLOBAL STATES for Toast and Loader (This makes them work)
    var isGlobalLoading by remember { mutableStateOf(false) }
    var globalLoadingMessage by remember { mutableStateOf("") }
    var showToast by remember { mutableStateOf(false) }
    var toastMessage by remember { mutableStateOf("") }

    val filteredSubs = remember(subscriptions, selectedTab, searchQuery) {
        val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 7) }
        subscriptions.filter { sub ->
            val matchesSearch = listOf("fullName", "email", "phone", "status").any {
                sub[it].toString().contains(searchQuery, ignoreCase = true)
            }
            val matchesTab = when (selectedTab) {
                0 -> sub["status"] != "deactivated"
                1 -> sub["status"] == "active" && (sub["endDate"] as? Timestamp)?.toDate()?.before(cal.time) == true
                2 -> sub["status"] == "deactivated"
                else -> true
            }
            matchesSearch && matchesTab
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(modifier = modifier, containerColor = Color.Transparent) { pad ->
            Column(modifier = Modifier.fillMaxSize().padding(bottom = pad.calculateBottomPadding())) {

                // Stat Boxes Row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 0.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    StatBox(Modifier.weight(1f), "Active", stats["active"].toString(), Icons.Filled.VerifiedUser, emeraldGreen)
                    StatBox(Modifier.weight(1f), "Expiring", stats["expiring"].toString(), Icons.Filled.HourglassTop, Color(0xFFFF9800))
                    StatBox(Modifier.weight(1f), "Disabled", stats["deactivated"].toString(), Icons.Filled.Block, Color.Red)
                    val rev = stats["revenue"]?.toString()?.toLongOrNull() ?: 0L
                    StatBox(Modifier.weight(1f), "Revenue", if(rev >= 1000) "₹${rev/1000}K" else "₹$rev", Icons.Filled.AttachMoney, Color.Yellow)
                }

                // Search Bar
                Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), shape = RoundedCornerShape(12.dp), color = glassBg, border = BorderStroke(1.dp, glassBorder)) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Search, null, Modifier.size(18.dp), emeraldGreen)
                        BasicTextField(
                            value = searchQuery, onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                            cursorBrush = SolidColor(emeraldGreen),
                            decorationBox = { inner ->
                                if (searchQuery.isEmpty()) {
                                    Text("Search subscribers...", color = Color.White.copy(0.3f), fontSize = 14.sp)
                                }
                                inner()
                            }
                        )
                    }
                }

                // Tabs
                TabRow(selectedTabIndex = selectedTab, containerColor = Color.Transparent, contentColor = Color.White, divider = {}) {
                    val tabTitles = listOf("All", "Expiring", "Disabled", "Plans")
                    tabTitles.forEachIndexed { i, t ->
                        val count = when(i) {
                            0 -> stats["active"] ?: 0
                            1 -> stats["expiring"] ?: 0
                            2 -> stats["deactivated"] ?: 0
                            else -> plans.size
                        }
                        Tab(selected = selectedTab == i, onClick = { selectedTab = i }, text = { Text("$t ($count)", fontSize = 10.sp, fontWeight = FontWeight.Bold) })
                    }
                }

                Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    if (selectedTab == 3) {
                        PlanContent(
                            plans = plans,
                            onAdd = { showAddEditPlan = emptyMap() },
                            onEdit = { showAddEditPlan = it },
                            onDelete = { planId, planName -> showDeletePlanDialog = Pair(planId, planName) },
                            isGlobalLoading = isGlobalLoading
                        )
                    } else {
                        SubscriptionContent(
                            list = filteredSubs,
                            onManage = { showManageSub = it },
                            onExtend = { showExtendSub = it },
                            isGlobalLoading = isGlobalLoading
                        )
                    }
                }
            }
        }

        // Loader Overlay (Stays on top)
        AnimatedVisibility(visible = isGlobalLoading, enter = fadeIn(), exit = fadeOut()) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).zIndex(10f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = emeraldGreen)
                    Spacer(Modifier.height(16.dp))
                    Text(globalLoadingMessage, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Toast Overlay (Stays on top even when dialogs close)
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

    showManageSub?.let {
        ManageSubDialog(
            sub = it,
            onDismiss = { showManageSub = null },
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

@Composable
private fun SubscriptionContent(
    list: List<Map<String, Any>>,
    onManage: (Map<String, Any>) -> Unit,
    onExtend: (Map<String, Any>) -> Unit,
    isGlobalLoading: Boolean
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(list, key = { it["id"].toString() }) { sub ->
            val isDeactivated = sub["status"] == "deactivated"
            val date = (sub["endDate"] as? Timestamp)?.toDate()
            val photoBase64 = sub["profileImage"]?.toString() ?: ""
            val imgSrc = if (photoBase64.isEmpty()) null else if (photoBase64.contains(",")) photoBase64.split(",")[1] else photoBase64

            val decodedBitmap = remember(imgSrc) {
                if (imgSrc != null) {
                    try {
                        val bytes = Base64.decode(imgSrc, Base64.DEFAULT)
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    } catch (e: Exception) { null }
                } else null
            }

            Card(
                modifier = Modifier.fillMaxWidth().animateContentSize(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = glassBg),
                border = BorderStroke(1.dp, glassBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (decodedBitmap != null) {
                            Image(bitmap = decodedBitmap, contentDescription = null, modifier = Modifier.size(45.dp).clip(CircleShape).border(1.dp, emeraldGreen, CircleShape), contentScale = ContentScale.Crop)
                        } else {
                            Box(modifier = Modifier.size(45.dp).background(Color.Gray.copy(0.3f), CircleShape), contentAlignment = Alignment.Center) {
                                Text(text = sub["fullName"].toString().take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = sub["fullName"].toString(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Email, null, Modifier.size(10.dp), Color.White.copy(0.4f))
                                Text(" EMAIL: ", color = Color.White.copy(0.5f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Text(sub["email"].toString(), color = Color.White.copy(0.7f), fontSize = 11.sp)
                            }
                        }
                        StatusPill(s = sub["status"].toString())
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Phone, null, Modifier.size(10.dp), Color.White.copy(0.4f))
                                Text(" PHONE ", color = Color.White.copy(0.5f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(sub["phone"].toString(), color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(start = 14.dp))
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("EXPIRES ", color = Color.White.copy(0.5f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Icon(Icons.Filled.CalendarMonth, null, Modifier.size(10.dp), Color.White.copy(0.4f))
                            }
                            Text(date?.let { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(it) } ?: "N/A", color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(end = 14.dp))
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Color.White.copy(0.05f))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Star, null, Modifier.size(12.dp), emeraldGreen)
                                Text(" PLAN: ", color = Color.White.copy(0.5f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Text(sub["planName"].toString(), color = Color.White, fontSize = 12.sp)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                                Icon(Icons.Filled.Payment, null, Modifier.size(12.dp), Color.Yellow)
                                Text(" PAY: ", color = Color.White.copy(0.5f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                Text(sub["paymentMethod"].toString(), color = Color.White.copy(0.8f), fontSize = 11.sp)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(onClick = { if (!isGlobalLoading) onManage(sub) }, modifier = Modifier.size(32.dp).background(Color.White.copy(0.1f), CircleShape), enabled = !isGlobalLoading) {
                                Icon(if (isDeactivated) Icons.Filled.Refresh else Icons.Filled.Settings, null, Modifier.size(16.dp), Color.White)
                            }
                            if (!isDeactivated) {
                                IconButton(onClick = { if (!isGlobalLoading) onExtend(sub) }, modifier = Modifier.size(32.dp).background(emeraldGreen.copy(0.2f), CircleShape), enabled = !isGlobalLoading) {
                                    Icon(Icons.Filled.AddCircle, null, Modifier.size(16.dp), emeraldGreen)
                                }
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
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = emeraldGreen))
                OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text("Price") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = emeraldGreen))
                OutlinedTextField(value = days, onValueChange = { days = it }, label = { Text("Days") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = emeraldGreen))
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
                        vm.updatePlan(plan["planId"].toString(), name, price.toIntOrNull() ?: 0, days.toIntOrNull() ?: 0)
                        onSuccess("Plan updated successfully")
                    }
                    onLoading("", false)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
            ) { Text("Save", color = emeraldGreen, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Color.White.copy(0.7f)) } }
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
        text = { Text("Are you sure you want to delete \"$planName\"? This action cannot be undone.", color = Color.White.copy(0.7f)) },
        confirmButton = {
            Button(
                onClick = {
                    onLoading("Deleting plan...", true)

                    // UPDATED: Passing both ID and Name for the log
                    vm.deletePlan(planId, planName)

                    onSuccess("Plan deleted successfully")
                    onLoading("", false)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
            ) { Text("Delete", color = Color.Red, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Color.White.copy(0.7f)) } }
    )
}

@Composable
private fun ManageSubDialog(
    sub: Map<String, Any>,
    onDismiss: () -> Unit,
    vm: SubscriptionViewModel,
    onLoading: (String, Boolean) -> Unit,
    onSuccess: (String) -> Unit
) {
    val isDeactivated = sub["status"] == "deactivated"
    val userName = sub["fullName"].toString() // EXTRACT NAME

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1C1E),
        title = { Text(if (isDeactivated) "Reactivate User" else "Deactivate User", color = Color.White) },
        text = { Text("Confirm status change for $userName?", color = Color.White.copy(0.7f)) },
        confirmButton = {
            Button(
                onClick = {
                    onLoading("Processing...", true)
                    if (isDeactivated) {
                        // UPDATED: Passing ID and Name
                        vm.activateSubscription(sub["id"].toString(), userName)
                        onSuccess("User reactivated")
                    } else {
                        // UPDATED: Passing ID and Name
                        vm.deactivateSubscription(sub["id"].toString(), userName)
                        onSuccess("User deactivated")
                    }
                    onLoading("", false)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
            ) { Text("Confirm", color = if (isDeactivated) emeraldGreen else Color.Red, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Color.White.copy(0.7f)) } }
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
    val userName = sub["fullName"].toString() // EXTRACT NAME

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1C1E),
        title = { Text("Extend Validity", color = Color.White) },
        text = {
            OutlinedTextField(
                value = days,
                onValueChange = { days = it.filter { c -> c.isDigit() } },
                label = { Text("Days") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = emeraldGreen)
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onLoading("Extending...", true)

                    // UPDATED: Passing ID, Name, and Days
                    vm.extendSubscription(
                        sub["id"].toString(),
                        userName,
                        days.toIntOrNull() ?: 0
                    )

                    onSuccess("Extended for $userName by $days days")
                    onLoading("", false)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
            ) { Text("Extend", color = emeraldGreen, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Color.White.copy(0.7f)) } }
    )
}

// --------------------------------------------------------------------------------
// STAT BOX, STATUS PILL, PLAN CONTENT (Remained as original)
// --------------------------------------------------------------------------------

@Composable
private fun StatBox(modifier: Modifier, title: String, value: String, icon: ImageVector, color: Color) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = glassBg), border = BorderStroke(1.dp, glassBorder), shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(14.dp), color.copy(alpha = 0.8f))
            Text(value, color = color, fontSize = 16.sp)
            Text(title.uppercase(), color = Color.White.copy(alpha = 0.5f), fontSize = 7.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StatusPill(s: String) {
    val c = when(s.lowercase()) {
        "active" -> emeraldGreen
        "expired" -> Color(0xFFE74C3C)
        else -> Color.Gray
    }
    Surface(color = c.copy(0.15f), shape = RoundedCornerShape(6.dp), border = BorderStroke(1.dp, c.copy(0.4f))) {
        Text(s.uppercase(), color = c, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp))
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
    Column {
        Button(
            onClick = onAdd,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = glassBg),
            shape = RoundedCornerShape(12.dp),
            enabled = !isGlobalLoading
        ) {
            Icon(Icons.Default.Add, null, tint = emeraldGreen)
            Text(" ADD NEW PLAN", color = emeraldGreen, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(plans) { plan ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = glassBg),
                    border = BorderStroke(1.dp, glassBorder),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // LEFT SIDE: Plan Information with Icons
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            // Plan Name
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Label, null, Modifier.size(14.dp), emeraldGreen)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = plan["name"].toString(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }

                            // Duration
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Timer, null, Modifier.size(12.dp), Color.White.copy(0.5f))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "${plan["duration"]} Days Access",
                                    color = Color.White.copy(0.6f),
                                    fontSize = 12.sp
                                )
                            }

                            // Price
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Payments, null, Modifier.size(12.dp), Color.Yellow.copy(0.8f))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "₹${plan["price"]}",
                                    color = emeraldGreen,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp
                                )
                            }
                        }

                        // RIGHT SIDE: Actions
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(
                                onClick = { if (!isGlobalLoading) onEdit(plan) },
                                modifier = Modifier.size(36.dp).background(Color.White.copy(0.05f), CircleShape),
                                enabled = !isGlobalLoading
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    null,
                                    Modifier.size(18.dp),
                                    Color.White.copy(0.7f)
                                )
                            }

                            IconButton(
                                onClick = { if (!isGlobalLoading) onDelete(plan["planId"].toString(), plan["name"].toString()) },
                                modifier = Modifier.size(36.dp).background(Color.Red.copy(0.1f), CircleShape),
                                enabled = !isGlobalLoading
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    null,
                                    Modifier.size(18.dp),
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