package com.manish.demo.ui.admin

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.zIndex
import com.manish.demo.ui.components.CustomToastCompose
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.firebase.auth.FirebaseAuth
import com.manish.demo.viewmodel.AdminViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsersManagementScreen(
    viewModel: AdminViewModel,
    modifier: Modifier = Modifier
) {
    val users by viewModel.allUsers.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var enlargedImage by remember { mutableStateOf<ImageBitmap?>(null) }

    // --- NEW STATES FOR LOADER AND TOAST ---
    var isActionLoading by remember { mutableStateOf(false) }
    var showToast by remember { mutableStateOf(false) }
    var toastMessage by remember { mutableStateOf("") }

    val currentAdminUid = remember { FirebaseAuth.getInstance().currentUser?.uid }

    LaunchedEffect(Unit) { viewModel.fetchUsers() }

    val filteredUsers = remember(users, searchQuery) {
        users.filter { user ->
            val uid = user["uid"]?.toString() ?: ""
            val name = user["fullName"]?.toString()?.lowercase() ?: ""
            val email = user["email"]?.toString()?.lowercase() ?: ""
            val phone = user["phone"]?.toString() ?: ""
            val query = searchQuery.lowercase()
            uid != currentAdminUid && (name.contains(query) || email.contains(query) || phone.contains(query))
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // --- 1. THE TOAST COMPONENT ---
        CustomToastCompose(
            message = toastMessage,
            showToast = showToast,
            onDismiss = { showToast = false }
        )

        Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("By Name,Email or Phone Number", color = Color.Gray) },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.White) },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color.White.copy(alpha = 0.05f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                    focusedBorderColor = Color(0xFF2ECC71),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(20.dp))

            if (users.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF2ECC71))
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(items = filteredUsers, key = { it["uid"].toString() }) { user ->
                        UserManagementCard(
                            user = user,
                            onPhotoClick = { enlargedImage = it },
                            onToggleAdmin = {
                                isActionLoading = true // START LOADER
                                viewModel.toggleAdminStatus(
                                    user["uid"].toString(),
                                    user["role"]?.toString().equals("admin", true)
                                ) { message ->
                                    // CALLBACK
                                    toastMessage = message
                                    showToast = true
                                    isActionLoading = false // STOP LOADER
                                }
                            },
                            onToggleBan = {
                                isActionLoading = true // START LOADER
                                viewModel.toggleBanStatus(
                                    user["uid"].toString(),
                                    user["isBanned"] as? Boolean ?: false
                                ) { message ->
                                    // CALLBACK
                                    toastMessage = message
                                    showToast = true
                                    isActionLoading = false // STOP LOADER
                                }
                            }
                        )
                    }
                }
            }
        }

        // --- 2. FULL SCREEN LOADER OVERLAY ---
        AnimatedVisibility(
            visible = isActionLoading,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .zIndex(5f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFF2ECC71))
                    Spacer(Modifier.height(12.dp))
                    Text("Updating user...", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        // --- ENLARGED PHOTO DIALOG ---
        enlargedImage?.let { bitmap ->
            Dialog(onDismissRequest = { enlargedImage = null }) {
                Box(
                    modifier = Modifier.fillMaxSize().clickable { enlargedImage = null },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        modifier = Modifier.size(300.dp).clip(CircleShape).border(3.dp, Color(0xFF2ECC71), CircleShape),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
    }
}

@Composable
fun UserManagementCard(
    user: Map<String, Any>,
    onPhotoClick: (ImageBitmap) -> Unit,
    onToggleAdmin: () -> Unit,
    onToggleBan: () -> Unit
) {
    val name = user["fullName"]?.toString() ?: "No Name"
    val email = user["email"]?.toString() ?: "No Email"
    val phone = user["phone"]?.toString() ?: "N/A"
    val dob = user["dob"]?.toString() ?: "Not set"
    val role = user["role"]?.toString() ?: "USER"
    val photoBase64 = user["profileImage"]?.toString() ?: ""
    val isBanned = user["isBanned"] as? Boolean ?: false
    val isAdmin = role.equals("admin", ignoreCase = true)

    var showMenu by remember { mutableStateOf(false) }

    // Decode Base64 image
    val decodedBitmap = remember(photoBase64) {
        if (photoBase64.isNotEmpty()) {
            try {
                val cleanStr = if (photoBase64.contains(",")) photoBase64.split(",")[1] else photoBase64
                val bytes = android.util.Base64.decode(cleanStr, android.util.Base64.DEFAULT)
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } catch (e: Exception) { null }
        } else null
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // ---------- Top section: Name, Email, Dropdown ----------
            Row(verticalAlignment = Alignment.CenterVertically) {
                // User photo
                Box(modifier = Modifier.size(56.dp).clickable { decodedBitmap?.let { onPhotoClick(it) } }) {
                    if (decodedBitmap != null) {
                        Image(
                            bitmap = decodedBitmap,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .border(1.dp, Color(0xFF2ECC71), CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize().background(Color.Gray.copy(0.3f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(email, color = Color.LightGray, fontSize = 12.sp, maxLines = 1)
                }

                // Dropdown menu
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, null, tint = Color.White)
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(Color(0xFF1E1E1E))
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (isAdmin) "Demote to User" else "Make Admin",
                                    color = if (isAdmin) Color.White else Color(0xFF2196F3)
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.AdminPanelSettings,
                                    contentDescription = null,
                                    tint = if (isAdmin) Color.Gray else Color(0xFF2196F3)
                                )
                            },
                            enabled = !isAdmin,
                            onClick = {
                                onToggleAdmin()
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (isBanned) "Unban User" else "Ban User",
                                    color = if (isBanned) Color.Green else Color.Red
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Block,
                                    contentDescription = null,
                                    tint = if (isBanned) Color.Green else Color.Red
                                )
                            },
                            enabled = !isAdmin,
                            onClick = {
                                onToggleBan()
                                showMenu = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

            // ---------- Bottom section ----------
            // First row: Phone & DOB
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InfoBlock("PHONE", phone, Icons.Default.Phone)
                InfoBlock("DOB", dob, Icons.Default.Cake)
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

            // Second row: Role + Status
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top  // Align to top
            ) {
                // Role section
                Column {
                    Text(
                        "ROLE",
                        color = Color.Gray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    // Role content with fixed height to match status chip
                    Box(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .height(24.dp)  // Fixed height to match status chip
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            Icon(
                                if (isAdmin) Icons.Default.AdminPanelSettings else Icons.Default.Person,
                                contentDescription = null,
                                tint = if (isAdmin) Color.Green else Color.LightGray,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                role.uppercase(),
                                color = if (isAdmin) Color.White else Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Status section
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        "STATUS",
                        color = Color.Gray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    // Status badge with same height
                    Box(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .height(24.dp)  // Same fixed height
                    ) {
                        StatusChip(
                            text = if (isBanned) "BANNED" else "ACTIVE",
                            color = if (isBanned) Color.Red else Color(0xFF2ECC71),
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun InfoBlock(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column {
        Text(label, color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
            Icon(icon, null, modifier = Modifier.size(12.dp), tint = Color(0xFF2ECC71))
            Spacer(Modifier.width(4.dp))
            Text(value, color = Color.White, fontSize = 12.sp)
        }
    }
}


@Composable
fun StatusChip(text: String, color: Color,modifier: Modifier = Modifier) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Text(text, color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}