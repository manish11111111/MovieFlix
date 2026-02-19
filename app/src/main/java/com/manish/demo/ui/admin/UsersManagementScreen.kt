package com.manish.demo.ui.admin

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Cake
import com.manish.demo.utils.getResponsiveSizes
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.zIndex
import com.manish.demo.ui.components.CustomToastCompose
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

    var isActionLoading by remember { mutableStateOf(false) }
    var showToast by remember { mutableStateOf(false) }
    var toastMessage by remember { mutableStateOf("") }

    var userForAdminAction by remember { mutableStateOf<Map<String, Any>?>(null) }
    var userForBanAction by remember { mutableStateOf<Map<String, Any>?>(null) }

    val currentAdminUid =
        remember { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid }

    LaunchedEffect(Unit) { viewModel.fetchUsers() }

    val filteredUsers = remember(users, searchQuery) {
        users.filter { user ->
            val uid = user["uid"]?.toString() ?: ""
            val name = user["fullName"]?.toString()?.lowercase() ?: ""
            val email = user["email"]?.toString()?.lowercase() ?: ""
            val phone = user["phone"]?.toString() ?: ""
            val query = searchQuery.lowercase()
            uid != currentAdminUid && (name.contains(query) || email.contains(query) || phone.contains(
                query
            ))
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CustomToastCompose(
            message = toastMessage,
            showToast = showToast,
            onDismiss = { showToast = false })

        Column(modifier = modifier
            .fillMaxSize()
            .padding(16.dp)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("By Name, Email or Phone Number", color = Color.Gray) },
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
                            onRequestAdminToggle = { userForAdminAction = user },
                            onRequestBanToggle = { userForBanAction = user }
                        )
                    }
                }
            }
        }

        // --- UPDATED ROLE ALERT ---
        userForAdminAction?.let { user ->
            AlertDialog(
                onDismissRequest = { userForAdminAction = null },
                containerColor = Color(0xFF1E1E1E),
                title = { Text("Promote User", color = Color.White) },
                text = { Text("Make ${user["fullName"]} an Admin?", color = Color.LightGray) },
                confirmButton = {
                    TextButton(onClick = {
                        val uid = user["uid"].toString()
                        val name = user["fullName"].toString() // GET NAME FOR LOG
                        userForAdminAction = null
                        isActionLoading = true
                        viewModel.toggleAdminStatus(uid, name, false) { msg ->
                            toastMessage = msg
                            showToast = true
                            isActionLoading = false
                        }
                    }) { Text("Promote", color = Color(0xFF2ECC71), fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        userForAdminAction = null
                    }) { Text("Cancel", color = Color.White) }
                }
            )
        }

        // --- UPDATED BAN ALERT ---
        userForBanAction?.let { user ->
            val isBanned = user["isBanned"] as? Boolean ?: false
            AlertDialog(
                onDismissRequest = { userForBanAction = null },
                containerColor = Color(0xFF1E1E1E),
                title = { Text(if (isBanned) "Unban User" else "Ban User", color = Color.White) },
                text = { Text("Confirm action for ${user["fullName"]}?", color = Color.LightGray) },
                confirmButton = {
                    TextButton(onClick = {
                        val uid = user["uid"].toString()
                        val name = user["fullName"].toString() // GET NAME FOR LOG
                        userForBanAction = null
                        isActionLoading = true
                        viewModel.toggleBanStatus(uid, name, isBanned) { msg ->
                            toastMessage = msg
                            showToast = true
                            isActionLoading = false
                        }
                    }) {
                        Text(
                            "Confirm",
                            color = if (isBanned) Color.Green else Color.Red,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { userForBanAction = null }) {
                        Text(
                            "Cancel",
                            color = Color.White
                        )
                    }
                }
            )
        }

        // Loader Overlay
        androidx.compose.animation.AnimatedVisibility(
            visible = isActionLoading,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .zIndex(10f), contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFF2ECC71))
                    Spacer(Modifier.height(12.dp))
                    Text("Updating user...", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Enlarged Image Dialog
        enlargedImage?.let { bitmap ->
            Dialog(onDismissRequest = { enlargedImage = null }) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { enlargedImage = null },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        modifier = Modifier
                            .size(300.dp)
                            .clip(CircleShape)
                            .border(3.dp, Color(0xFF2ECC71), CircleShape),
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
    onRequestAdminToggle: () -> Unit,
    onRequestBanToggle: () -> Unit
) {
    val name = user["fullName"]?.toString() ?: "No Name"
    val email = user["email"]?.toString() ?: "No Email"
    val phone = user["phone"]?.toString() ?: "N/A"
    val dob = user["dob"]?.toString() ?: "Not set"
    val role = user["role"]?.toString() ?: "USER"
    val photoBase64 = user["profileImage"]?.toString() ?: ""
    val isBanned = user["isBanned"] as? Boolean ?: false
    val isAdmin = role.equals("admin", ignoreCase = true)
    val sizes = getResponsiveSizes()

    var showMenu by remember { mutableStateOf(false) }

    val decodedBitmap = remember(photoBase64) {
        if (photoBase64.isNotEmpty()) {
            try {
                val cleanStr = if (photoBase64.contains(",")) photoBase64.split(",")[1] else photoBase64
                val bytes = android.util.Base64.decode(cleanStr, android.util.Base64.DEFAULT)
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } catch (e: Exception) { null }
        } else null
    }

    val avatarSize = sizes.iconLarge + 8.dp // 56dp / 72dp / 80dp

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(sizes.paddingMedium)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(avatarSize).clickable { decodedBitmap?.let { onPhotoClick(it) } }) {
                    if (decodedBitmap != null) {
                        Image(
                            bitmap = decodedBitmap,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(CircleShape).border(1.dp, Color(0xFF2ECC71), CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize().background(Color.Gray.copy(0.3f), CircleShape), contentAlignment = Alignment.Center) {
                            Text(name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = sizes.subtitleSize)
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f).padding(start = sizes.paddingSmall)) {
                    Text(name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = sizes.subtitleSize)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(email, color = Color.LightGray, fontSize = sizes.captionSize, maxLines = 1)
                }

                if (!isAdmin) {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, null, tint = Color.White)
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, modifier = Modifier.background(Color(0xFF1E1E1E))) {
                            DropdownMenuItem(
                                text = { Text("Make Admin", color = if (isBanned) Color.Gray else Color(0xFF2196F3), fontSize = sizes.bodySize) },
                                leadingIcon = { Icon(Icons.Default.AdminPanelSettings, null, tint = if (isBanned) Color.Gray else Color(0xFF2196F3)) },
                                enabled = !isBanned,
                                onClick = { showMenu = false; onRequestAdminToggle() }
                            )
                            DropdownMenuItem(
                                text = { Text(if (isBanned) "Unban User" else "Ban User", color = if (isBanned) Color.Green else Color.Red, fontSize = sizes.bodySize) },
                                leadingIcon = { Icon(Icons.Default.Block, null, tint = if (isBanned) Color.Green else Color.Red) },
                                onClick = { showMenu = false; onRequestBanToggle() }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(sizes.paddingSmall))
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

            Row(modifier = Modifier.fillMaxWidth().padding(top = sizes.paddingSmall), horizontalArrangement = Arrangement.SpaceBetween) {
                InfoBlock("PHONE", phone, Icons.Default.Phone, sizes)
                InfoBlock("DOB", dob, Icons.Default.Cake, sizes)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = sizes.paddingSmall), color = Color.White.copy(alpha = 0.1f))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("ROLE", color = Color.Gray, fontSize = sizes.smallSize, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(sizes.iconSmall + 8.dp)) {
                        Icon(
                            if (isAdmin) Icons.Default.AdminPanelSettings else Icons.Default.Person,
                            null,
                            tint = if (isAdmin) Color.Green else Color.LightGray,
                            modifier = Modifier.size(sizes.iconSmall)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(role.uppercase(), color = Color.White, fontSize = sizes.captionSize, fontWeight = FontWeight.Medium)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("STATUS", color = Color.Gray, fontSize = sizes.smallSize, fontWeight = FontWeight.Bold)
                    StatusChip(
                        text = if (isBanned) "BANNED" else "ACTIVE",
                        color = if (isBanned) Color.Red else Color(0xFF2ECC71),
                        modifier = Modifier.height(sizes.iconSmall + 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun InfoBlock(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, sizes: com.manish.demo.utils.ResponsiveSizes) {
    Column {
        Text(label, color = Color.Gray, fontSize = sizes.smallSize, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
            Icon(icon, null, modifier = Modifier.size(sizes.iconSmall - 4.dp), tint = Color(0xFF2ECC71))
            Spacer(Modifier.width(4.dp))
            Text(value, color = Color.White, fontSize = sizes.captionSize)
        }
    }
}

@Composable
fun StatusChip(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Text(
            text,
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}