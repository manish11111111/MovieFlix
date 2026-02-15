package com.manish.demo

import androidx.compose.ui.platform.LocalContext

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.google.accompanist.swiperefresh.SwipeRefreshIndicator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.manish.demo.ui.admin.MoviesManagementScreen
import com.manish.demo.ui.ChangePasswordDialog
import com.manish.demo.ui.InfoRow
import com.manish.demo.ui.admin.UsersManagementScreen
import com.manish.demo.ui.components.CustomToastCompose
import com.manish.demo.ui.components.ImageSelectionDialog
import com.manish.demo.ui.theme.DemoTheme
import com.manish.demo.viewmodel.AdminViewModel
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

class AdminHomeActivity : ComponentActivity() {
    private var adminName by mutableStateOf("Admin")
    private var adminEmail by mutableStateOf("")
    private var adminPhone by mutableStateOf("")
    private var adminDob by mutableStateOf("")
    private var adminImageBitmap by mutableStateOf<Bitmap?>(null)
    private var passwordLastUpdated by mutableStateOf("Never")
    private var isRefreshing by mutableStateOf(false)
    private var showSuccessToast by mutableStateOf(false)
    private var isUploadingImage by mutableStateOf(false)

    // Activity result launchers
    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) handleImageSelection(uri, "gallery")
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) handleCameraImage(bitmap)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        listenForRoleSecurity()
        fetchAdminData()
    }

    private fun fetchAdminData() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            FirebaseFirestore.getInstance().collection("users").document(userId)
                .get(Source.SERVER)
                .addOnSuccessListener { document ->
                    if (document != null) {
                        adminName = document.getString("fullName") ?: "Admin"
                        adminEmail = document.getString("email") ?: ""
                        adminPhone = document.getString("phone") ?: ""
                        adminDob = document.getString("dob") ?: ""

                        val lastUpdated = document.getTimestamp("passwordLastUpdated")?.toDate()
                        passwordLastUpdated = if (lastUpdated != null) {
                            SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()).format(lastUpdated)
                        } else "Never"

                        val base64Image = document.getString("profileImage")
                        if (base64Image != null) {
                            try {
                                val decodedBytes = Base64.decode(base64Image, Base64.DEFAULT)
                                adminImageBitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                            } catch (e: Exception) { Log.e("Admin", "Image error", e) }
                        }
                    }
                    isRefreshing = false
                    updateUI()
                }
                .addOnFailureListener { isRefreshing = false; updateUI() }
        } else {
            isRefreshing = false
            updateUI()
        }
    }

    private fun updateUI() {
        setContent {
            DemoTheme {
                AdminApp(
                    adminName = adminName,
                    adminEmail = adminEmail,
                    adminPhone = adminPhone,
                    adminDob = adminDob,
                    adminImageBitmap = adminImageBitmap,
                    passwordLastUpdated = passwordLastUpdated,
                    isRefreshing = isRefreshing,
                    showSuccessToast = showSuccessToast,
                    isUploadingImage = isUploadingImage,
                    onRefresh = {
                        if (!isUploadingImage) {
                            isRefreshing = true
                            fetchAdminData()
                        }
                    },
                    onHideSuccessToast = { showSuccessToast = false },
                    onImageSelected = { handleImageSourceSelection(it) }
                )
            }
        }
    }

    private fun handleImageSourceSelection(sourceType: String) {
        when (sourceType) {
            "gallery" -> openGallery()
            "camera" -> if (checkPermission(android.Manifest.permission.CAMERA)) openCamera() else requestPermission(android.Manifest.permission.CAMERA, 101)
        }
    }

    private fun openGallery() = galleryLauncher.launch("image/*")
    private fun openCamera() = cameraLauncher.launch(null)

    private fun handleImageSelection(imageUri: Uri, sourceType: String) {
        isUploadingImage = true; updateUI()
        try {
            val inputStream: InputStream? = contentResolver.openInputStream(imageUri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            bitmap?.let { updateProfileImageInFirebase(convertBitmapToBase64(it)) }
        } catch (e: Exception) { isUploadingImage = false; updateUI() }
    }

    private fun handleCameraImage(bitmap: Bitmap) {
        isUploadingImage = true; updateUI()
        updateProfileImageInFirebase(convertBitmapToBase64(bitmap))
    }

    private fun convertBitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT)
    }

    private fun updateProfileImageInFirebase(base64Image: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        userId?.let {
            FirebaseFirestore.getInstance().collection("users").document(it)
                .update("profileImage", base64Image)
                .addOnSuccessListener {
                    showSuccessToast = true; isUploadingImage = false; fetchAdminData()
                }
                .addOnFailureListener { isUploadingImage = false; updateUI() }
        }
    }
    private var initialRole: String? = null

    private fun listenForRoleSecurity() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            FirebaseFirestore.getInstance().collection("users").document(user.uid)
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null && snapshot.exists()) {
                        val role = snapshot.getString("role") ?: "USER"

                        if (initialRole == null) {
                            initialRole = role
                        } else if (!initialRole.equals(role, ignoreCase = true)) {
                            // This triggers if an Admin is demoted to User
                            showSecurityDialog()
                        }
                    }
                }
        }
    }

    private fun showSecurityDialog() {
        if (!isFinishing) {
            android.app.AlertDialog.Builder(this)
                .setTitle("Permissions Changed")
                .setMessage("Your administrative roles have been updated. Please login again.")
                .setCancelable(false)
                .setPositiveButton("LOGIN") { _, _ ->
                    FirebaseAuth.getInstance().signOut()
                    val intent = Intent(this, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
                .show()
        }
    }
    private fun checkPermission(permission: String) = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    private fun requestPermission(permission: String, code: Int) = ActivityCompat.requestPermissions(this, arrayOf(permission), code)
}

enum class AdminDestinations(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    MOVIES("Movies", Icons.Default.Movie),
    USERS("Users", Icons.Default.Person),
    SUBSCRIPTIONS("Subs", Icons.Default.Subscriptions), // ADD THIS
    PROFILE("Profile", Icons.Default.AccountCircle),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminApp(
    adminName: String, adminEmail: String, adminPhone: String, adminDob: String,
    adminImageBitmap: Bitmap?, passwordLastUpdated: String, isRefreshing: Boolean,
    showSuccessToast: Boolean, isUploadingImage: Boolean, onRefresh: () -> Unit,
    onHideSuccessToast: () -> Unit, onImageSelected: (String) -> Unit
) {
    var currentDestination by rememberSaveable { mutableStateOf(AdminDestinations.HOME) }
    var showImageDialog by remember { mutableStateOf(false) }
    var showImageSelectionDialog by remember { mutableStateOf(false) }
    var showExitConfirmation by remember { mutableStateOf(false) }

    val adminViewModel: AdminViewModel = viewModel() // ViewModel stays alive at this level
    val context = LocalContext.current
    val activity = context as? Activity

    var passwordDialogOpen by remember { mutableStateOf(false) }
    var passwordChangeSuccess by remember { mutableStateOf(false) }
    var showCustomToast by remember { mutableStateOf(false) }
    var toastMessage by remember { mutableStateOf("") }

    BackHandler(enabled = !showExitConfirmation && !passwordDialogOpen) {
        if (currentDestination != AdminDestinations.HOME) currentDestination = AdminDestinations.HOME
        else showExitConfirmation = true
    }

    LaunchedEffect(showSuccessToast) {
        if (showSuccessToast) {
            toastMessage = "Picture updated successfully"; showCustomToast = true
            delay(2000); onHideSuccessToast()
        }
    }

    val gradient = Brush.linearGradient(listOf(Color(0xFF450457), Color(0xFF120017), Color(0xFF000000)))

    Box(modifier = Modifier.fillMaxSize().background(gradient)) {
        CustomToastCompose(message = toastMessage, showToast = showCustomToast, onDismiss = { showCustomToast = false })

        if (isUploadingImage) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.7f)).zIndex(2f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Text("Uploading image...", color = Color.White, modifier = Modifier.padding(top = 16.dp))
                }
            }
        }

        Scaffold(
            topBar = {
                // Hide TopBar for Movies to avoid double headers
                if (currentDestination != AdminDestinations.MOVIES) {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_logo_m), // <-- your drawable
                                    contentDescription = "Logo",
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("MovieFlix", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )
                }
            },
            bottomBar = {
                NavigationBar(containerColor = Color.Black.copy(0.3f)) {
                    AdminDestinations.entries.forEach { item ->
                        NavigationBarItem(
                            icon = { Icon(item.icon, item.label) },
                            label = { Text(item.label) },
                            selected = item == currentDestination,
                            onClick = { if (!isUploadingImage) currentDestination = item },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White,
                                selectedTextColor = Color.White,
                                unselectedIconColor = Color.Gray,
                                indicatorColor = Color.White.copy(0.2f)
                            )
                        )
                    }
                }
            },
            containerColor = Color.Transparent
        ) { innerPadding ->
            val contentModifier = Modifier.fillMaxSize().padding(innerPadding)

            SwipeRefresh(
                state = rememberSwipeRefreshState(isRefreshing = isRefreshing),
                onRefresh = onRefresh,
                swipeEnabled = currentDestination != AdminDestinations.MOVIES && !isUploadingImage,
                modifier = contentModifier
            ) {
                when (currentDestination) {
                    AdminDestinations.HOME -> AdminHomeContent(adminName)
                    AdminDestinations.MOVIES -> MoviesManagementScreen(viewModel = adminViewModel, modifier = Modifier.fillMaxSize())
                    AdminDestinations.USERS -> UsersManagementScreen(viewModel = adminViewModel, modifier = Modifier.fillMaxSize())
                    AdminDestinations.PROFILE -> AdminProfileContent(
                        adminName, adminEmail, adminPhone, adminDob, adminImageBitmap,
                        passwordLastUpdated, isUploadingImage, { showImageDialog = true },
                        { showImageSelectionDialog = true }, { passwordDialogOpen = true }
                    )
                    else -> AdminContent(currentDestination.label)
                }
            }
        }

        // Dialogs
        if (showExitConfirmation) {
            AlertDialog(
                onDismissRequest = { showExitConfirmation = false },
                title = { Text("Exit App?") },
                text = { Text("Are you sure you want to exit?") },
                confirmButton = { Button(onClick = { activity?.finish() }) { Text("EXIT") } },
                dismissButton = { TextButton(onClick = { showExitConfirmation = false }) { Text("CANCEL") } }
            )
        }

        if (showImageSelectionDialog) {
            ImageSelectionDialog(
                onDismiss = { showImageSelectionDialog = false },
                onGallerySelected = { showImageSelectionDialog = false; onImageSelected("gallery") },
                onCameraSelected = { showImageSelectionDialog = false; onImageSelected("camera") }
            )
        }

        if (passwordDialogOpen) {
            ChangePasswordDialog(
                onDismiss = { passwordDialogOpen = false },
                onPasswordChanged = { message ->
                    passwordDialogOpen = false        // close dialog
                    toastMessage = message            // set toast message
                    showCustomToast = true            // show custom toast
                    onRefresh()                       // refresh profile data
                }
            )
        }

    }
}

@Composable
fun AdminHomeContent(adminName: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.AdminPanelSettings, null, tint = Color.White, modifier = Modifier.size(80.dp))
            Text("Welcome, $adminName", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun AdminContent(title: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "$title Screen", color = Color.White, fontSize = 20.sp)
    }
}
@Composable
fun AdminProfileContent(
    adminName: String,
    adminEmail: String,
    adminPhone: String,
    adminDob: String,
    adminImageBitmap: Bitmap?,
    passwordLastUpdated: String,
    isUploadingImage: Boolean,
    onShowImageDialog: () -> Unit,
    onEditImageClicked: () -> Unit,
    onPasswordChangeClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Profile Image Section
        Box(contentAlignment = Alignment.Center) {
            if (adminImageBitmap != null) {
                Image(
                    bitmap = adminImageBitmap.asImageBitmap(),
                    contentDescription = "Profile Picture",
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .border(2.dp, Color.White, CircleShape)
                        .clickable { if (!isUploadingImage) onShowImageDialog() },
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Default.AccountCircle,
                    null,
                    modifier = Modifier.size(120.dp).clickable { if (!isUploadingImage) onShowImageDialog() },
                    tint = Color.LightGray
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(adminName, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.AdminPanelSettings,
                contentDescription = "Role",
                tint = Color.Blue,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Administrator",
                fontSize = 14.sp,
                color = Color.LightGray
            )
        }

        Spacer(Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.1f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                InfoRow(icon = Icons.Default.Email, text = adminEmail)
                HorizontalDivider(color = Color.White.copy(0.2f))
                InfoRow(icon = Icons.Default.Phone, text = adminPhone)
                HorizontalDivider(color = Color.White.copy(0.2f))
                InfoRow(icon = Icons.Default.Cake, text = adminDob)
                HorizontalDivider(color = Color.White.copy(0.2f))
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { if (!isUploadingImage) onPasswordChangeClicked() }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Lock, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Password", color = Color.White)
                        Text("Last updated: $passwordLastUpdated", fontSize = 12.sp, color = Color.LightGray)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = Color.White)
                }
            }
        }

        Spacer(Modifier.height(32.dp))
        Button(
            onClick = {
                // Sign out
                FirebaseAuth.getInstance().signOut()

                // Use the context to navigate and CLEAR the app's memory (prevents freeze)
                val intent = Intent(context, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                context.startActivity(intent)
                (context as Activity).finish()
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(0.7f))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Logout,
                    contentDescription = "Logout",
                    tint = Color.White
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "Logout",
                    color = Color.White
                )
            }

        }
    }

}


// AdminProfileContent and other helper composables stay the same as your original code