package com.manish.demo

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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.google.accompanist.swiperefresh.SwipeRefreshIndicator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import com.manish.demo.screens.admin.MoviesManagementScreen
import com.manish.demo.ui.ChangePasswordDialog
import com.manish.demo.ui.InfoRow
import com.manish.demo.ui.components.CustomToastCompose
import com.manish.demo.ui.components.ImageSelectionDialog
import com.manish.demo.ui.theme.DemoTheme
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
        if (uri != null) {
            Log.d("AdminHomeActivity", "Gallery image selected: $uri")
            handleImageSelection(uri, "gallery")
        } else {
            Log.d("AdminHomeActivity", "Gallery launcher returned null")
        }
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            Log.d("AdminHomeActivity", "Camera image captured")
            handleCameraImage(bitmap)
        } else {
            Log.d("AdminHomeActivity", "Camera launcher returned null")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        Log.d("AdminHomeActivity", "Activity created")

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("AdminHomeActivity", "Uncaught exception in thread: ${thread.name}", throwable)
        }

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
                        if (lastUpdated != null) {
                            passwordLastUpdated = SimpleDateFormat(
                                "MMM d, yyyy h:mm a",
                                Locale.getDefault()
                            ).format(lastUpdated)
                        } else {
                            passwordLastUpdated = "Never"
                        }

                        val base64Image = document.getString("profileImage")
                        if (base64Image != null) {
                            try {
                                val decodedBytes = Base64.decode(base64Image, Base64.DEFAULT)
                                adminImageBitmap = BitmapFactory.decodeByteArray(
                                    decodedBytes,
                                    0,
                                    decodedBytes.size
                                )
                                Log.d("AdminHomeActivity", "Profile image loaded from Firebase")
                            } catch (e: Exception) {
                                Log.e("AdminHomeActivity", "Error decoding image", e)
                            }
                        }
                    }
                    isRefreshing = false
                    updateUI()
                }
                .addOnFailureListener { exception ->
                    Log.e("AdminHomeActivity", "Error fetching admin data", exception)
                    isRefreshing = false
                    updateUI()
                }
        } else {
            isRefreshing = false
            updateUI()
        }
    }

    private fun updateUI() {
        try {
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
                        onHideSuccessToast = {
                            showSuccessToast = false
                        },
                        onImageSelected = { sourceType ->
                            handleImageSourceSelection(sourceType)
                        }
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("AdminHomeActivity", "Error in setContent", e)
        }
    }

    // Handle image source selection (gallery or camera)
    private fun handleImageSourceSelection(sourceType: String) {
        Log.d("AdminHomeActivity", "handleImageSourceSelection called with: $sourceType")

        when (sourceType) {
            "gallery" -> {
                Log.d("AdminHomeActivity", "Gallery selected")
                try {
                    openGallery()
                } catch (e: SecurityException) {
                    Log.w("AdminHomeActivity", "SecurityException, requesting permission")
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        val permission = android.Manifest.permission.READ_EXTERNAL_STORAGE
                        if (checkPermission(permission)) {
                            openGallery()
                        } else {
                            requestPermission(permission, 100)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("AdminHomeActivity", "Error in openGallery", e)
                    Toast.makeText(this, "Cannot open gallery", Toast.LENGTH_SHORT).show()
                }
            }
            "camera" -> {
                Log.d("AdminHomeActivity", "Camera selected")
                if (checkPermission(android.Manifest.permission.CAMERA)) {
                    Log.d("AdminHomeActivity", "Camera permission granted")
                    openCamera()
                } else {
                    Log.d("AdminHomeActivity", "Requesting CAMERA permission")
                    requestPermission(android.Manifest.permission.CAMERA, 101)
                }
            }
        }
    }

    private fun openGallery() {
        try {
            Log.d("AdminHomeActivity", "Opening gallery")
            galleryLauncher.launch("image/*")
        } catch (e: Exception) {
            Log.e("AdminHomeActivity", "Error opening gallery", e)
            Toast.makeText(this, "Cannot open gallery. Please check if you have a gallery app installed.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openCamera() {
        try {
            Log.d("AdminHomeActivity", "Opening camera")
            cameraLauncher.launch(null)
        } catch (e: Exception) {
            Log.e("AdminHomeActivity", "Error opening camera", e)
            Toast.makeText(this, "Cannot open camera", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleImageSelection(imageUri: Uri, sourceType: String) {
        isUploadingImage = true
        updateUI()

        try {
            Log.d("AdminHomeActivity", "handleImageSelection called with URI: $imageUri")
            val inputStream: InputStream? = contentResolver.openInputStream(imageUri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmap != null) {
                Log.d("AdminHomeActivity", "Bitmap decoded successfully, size: ${bitmap.width}x${bitmap.height}")
                val base64Image = convertBitmapToBase64(bitmap)
                updateProfileImageInFirebase(base64Image)
            } else {
                Log.e("AdminHomeActivity", "Bitmap is null after decoding")
                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
                isUploadingImage = false
                updateUI()
            }
        } catch (e: Exception) {
            Log.e("AdminHomeActivity", "Error processing image", e)
            Toast.makeText(this, "Error processing image: ${e.message}", Toast.LENGTH_SHORT).show()
            isUploadingImage = false
            updateUI()
        }
    }

    private fun handleCameraImage(bitmap: Bitmap) {
        isUploadingImage = true
        updateUI()

        try {
            Log.d("AdminHomeActivity", "Camera image captured, size: ${bitmap.width}x${bitmap.height}")
            val base64Image = convertBitmapToBase64(bitmap)
            updateProfileImageInFirebase(base64Image)
        } catch (e: Exception) {
            Log.e("AdminHomeActivity", "Error processing camera image", e)
            Toast.makeText(this, "Error processing camera image", Toast.LENGTH_SHORT).show()
            isUploadingImage = false
            updateUI()
        }
    }

    private fun convertBitmapToBase64(bitmap: Bitmap): String {
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }

    private fun updateProfileImageInFirebase(base64Image: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            FirebaseFirestore.getInstance().collection("users").document(userId)
                .update("profileImage", base64Image)
                .addOnSuccessListener {
                    Log.d("AdminHomeActivity", "Profile image updated successfully")
                    showSuccessToast = true
                    isUploadingImage = false
                    fetchAdminData()
                    Toast.makeText(this, "Profile image updated", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Log.e("AdminHomeActivity", "Error updating profile image", e)
                    Toast.makeText(this, "Failed to update profile: ${e.message}", Toast.LENGTH_SHORT).show()
                    isUploadingImage = false
                    updateUI()
                }
        } else {
            isUploadingImage = false
            updateUI()
        }
    }

    private fun checkPermission(permission: String): Boolean {
        val result = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        Log.d("AdminHomeActivity", "checkPermission for $permission: $result")
        return result
    }

    private fun requestPermission(permission: String, requestCode: Int) {
        Log.d("AdminHomeActivity", "requestPermission for $permission with code $requestCode")
        ActivityCompat.requestPermissions(this, arrayOf(permission), requestCode)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        Log.d("AdminHomeActivity", "onRequestPermissionsResult called: requestCode=$requestCode, permissions=${permissions.joinToString()}")

        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d("AdminHomeActivity", "Permission granted for request code: $requestCode")
            when (requestCode) {
                100 -> {
                    Toast.makeText(this, "Gallery permission granted", Toast.LENGTH_SHORT).show()
                    openGallery()
                }
                101 -> {
                    Toast.makeText(this, "Camera permission granted", Toast.LENGTH_SHORT).show()
                    openCamera()
                }
            }
        } else {
            Log.d("AdminHomeActivity", "Permission denied for request code: $requestCode")
            Toast.makeText(
                this,
                "Permission denied. You can enable it in app settings.",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}

enum class AdminDestinations(
    val label: String,
    val icon: ImageVector,
) {
    HOME("Home", Icons.Default.Home),
    MOVIES("Movies", Icons.Default.Movie),
    CATEGORIES("Categories", Icons.Default.List),
    USERS("Users", Icons.Default.Person),
    PROFILE("Profile", Icons.Default.AccountCircle),
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminApp(
    adminName: String,
    adminEmail: String,
    adminPhone: String,
    adminDob: String,
    adminImageBitmap: Bitmap?,
    passwordLastUpdated: String,
    isRefreshing: Boolean,
    showSuccessToast: Boolean,
    isUploadingImage: Boolean,
    onRefresh: () -> Unit,
    onHideSuccessToast: () -> Unit,
    onImageSelected: (String) -> Unit
) {
    var currentDestination by rememberSaveable { mutableStateOf(AdminDestinations.HOME) }
    var showImageDialog by remember { mutableStateOf(false) }
    var showImageSelectionDialog by remember { mutableStateOf(false) }

    // State for Exit Confirmation Dialog
    var showExitConfirmation by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val activity = context as? Activity

    var passwordDialogOpen by remember { mutableStateOf(false) }
    var passwordChangeSuccess by remember { mutableStateOf(false) }
    var showCustomToast by remember { mutableStateOf(false) }
    var toastMessage by remember { mutableStateOf("") }

    var localImageBitmap by remember { mutableStateOf(adminImageBitmap) }

    // --- NAVIGATION BACK HANDLER LOGIC ---
    BackHandler(enabled = !showExitConfirmation && !passwordDialogOpen && !showImageDialog && !showImageSelectionDialog) {
        if (currentDestination != AdminDestinations.HOME) {
            // If on any other tab, go back to Home
            currentDestination = AdminDestinations.HOME
        } else {
            // If on Home tab, ask to exit
            showExitConfirmation = true
        }
    }

    // Show success toast when image is updated
    LaunchedEffect(showSuccessToast) {
        if (showSuccessToast) {
            toastMessage = "Picture updated successfully"
            showCustomToast = true
            delay(2000)
            onHideSuccessToast()
        }
    }

    LaunchedEffect(adminImageBitmap) {
        localImageBitmap = adminImageBitmap
    }

    LaunchedEffect(passwordChangeSuccess) {
        if (passwordChangeSuccess) {
            currentDestination = AdminDestinations.PROFILE
            onRefresh()
            passwordChangeSuccess = false
            toastMessage = "Password updated successfully"
            showCustomToast = true
        }
    }

    val gradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFF450457),
            Color(0xFF120017),
            Color(0xFF000000)
        )
    )

    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            delay(1000)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradient)
    ) {
        CustomToastCompose(
            message = toastMessage,
            showToast = showCustomToast,
            onDismiss = { showCustomToast = false }
        )

        // Show uploading loader
        if (isUploadingImage) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .zIndex(2f), // Ensure it sits on top
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(50.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Uploading image...",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Scaffold(
            topBar = {
                // Hide TopBar on Movies screen as it has its own header
                if (currentDestination != AdminDestinations.MOVIES) {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_logo_m),
                                    contentDescription = "Logo",
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("MovieFlix", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent
                        )
                    )
                }
            },
            bottomBar = {
                NavigationBar(
                    containerColor = Color.Transparent,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.3f))
                ) {
                    AdminDestinations.entries.forEach { item ->
                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            selected = item == currentDestination,
                            onClick = {
                                if (!isUploadingImage) {
                                    currentDestination = item
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.White,
                                selectedTextColor = Color.White,
                                unselectedIconColor = Color.LightGray,
                                unselectedTextColor = Color.LightGray,
                                indicatorColor = Color.White.copy(alpha = 0.2f)
                            )
                        )
                    }
                }
            },
            containerColor = Color.Transparent
        ) { innerPadding ->

            // Content Wrapper to handle Padding and Swipe Refresh
            val contentModifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)

            if (!isUploadingImage) {
                SwipeRefresh(
                    state = rememberSwipeRefreshState(isRefreshing = isRefreshing),
                    onRefresh = onRefresh,
                    // Disable swipe refresh on Movies screen to prevent conflict with internal scrolling
                    swipeEnabled = currentDestination != AdminDestinations.MOVIES,
                    indicator = { state, trigger ->
                        SwipeRefreshIndicator(
                            state = state,
                            refreshTriggerDistance = trigger,
                            backgroundColor = Color.Transparent,
                            contentColor = Color.White
                        )
                    },
                    modifier = contentModifier
                ) {
                    // --- MAIN CONTENT SWITCHER ---
                    when (currentDestination) {
                        AdminDestinations.HOME -> AdminHomeContent(
                            adminName = adminName,
                            modifier = Modifier.fillMaxSize()
                        )
                        // INTEGRATED MOVIES SCREEN
                        AdminDestinations.MOVIES -> {
                            MoviesManagementScreen(modifier = Modifier.fillMaxSize())
                        }
                        AdminDestinations.PROFILE -> AdminProfileContent(
                            adminName = adminName,
                            adminEmail = adminEmail,
                            adminPhone = adminPhone,
                            adminDob = adminDob,
                            adminImageBitmap = localImageBitmap,
                            passwordLastUpdated = passwordLastUpdated,
                            isUploadingImage = isUploadingImage,
                            onShowImageDialog = { showImageDialog = true },
                            onEditImageClicked = { showImageSelectionDialog = true },
                            onPasswordChangeClicked = { passwordDialogOpen = true },
                            modifier = Modifier.fillMaxSize()
                        )
                        else -> AdminContent(
                            title = currentDestination.label,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            } else {
                // View when uploading (non-interactive)
                Box(modifier = contentModifier) {
                    // Keep displaying current destination content in background
                    if (currentDestination == AdminDestinations.PROFILE) {
                        AdminProfileContent(
                            adminName = adminName,
                            adminEmail = adminEmail,
                            adminPhone = adminPhone,
                            adminDob = adminDob,
                            adminImageBitmap = localImageBitmap,
                            passwordLastUpdated = passwordLastUpdated,
                            isUploadingImage = true,
                            onShowImageDialog = {},
                            onEditImageClicked = {},
                            onPasswordChangeClicked = {},
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        // --- DIALOGS ---

        // 1. Exit Confirmation Dialog
        if (showExitConfirmation) {
            AlertDialog(
                onDismissRequest = { showExitConfirmation = false },
                containerColor = Color(0xFF1A1A2E),
                title = { Text("Exit App?", color = Color.White, fontWeight = FontWeight.Bold) },
                text = { Text("Are you sure you want to close the application?", color = Color.White.copy(0.7f)) },
                confirmButton = {
                    Button(
                        onClick = { activity?.finish() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) {
                        Text("EXIT")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showExitConfirmation = false }) {
                        Text("STAY", color = Color.White)
                    }
                }
            )
        }

        if (showImageSelectionDialog) {
            ImageSelectionDialog(
                onDismiss = { showImageSelectionDialog = false },
                onGallerySelected = {
                    showImageSelectionDialog = false
                    onImageSelected("gallery")
                },
                onCameraSelected = {
                    showImageSelectionDialog = false
                    onImageSelected("camera")
                }
            )
        }

        if (passwordDialogOpen) {
            ChangePasswordDialog(
                onDismiss = { passwordDialogOpen = false },
                onPasswordChanged = {
                    passwordDialogOpen = false
                    passwordChangeSuccess = true
                }
            )
        }

        if (showImageDialog && localImageBitmap != null) {
            Dialog(onDismissRequest = { showImageDialog = false }) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.8f))
                        .clickable { showImageDialog = false },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = localImageBitmap!!.asImageBitmap(),
                        contentDescription = "Profile Picture",
                        modifier = Modifier
                            .size(300.dp)
                            .clip(CircleShape)
                            .border(2.dp, Color.White, CircleShape),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
    }
}

@Composable
fun AdminHomeContent(adminName: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                Icons.Default.AdminPanelSettings,
                contentDescription = "Admin",
                tint = Color.White,
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Welcome, $adminName!",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Administrator Dashboard",
                fontSize = 16.sp,
                color = Color.LightGray
            )
        }
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
            Box(contentAlignment = Alignment.BottomEnd) {
                if (adminImageBitmap != null) {
                    Image(
                        bitmap = adminImageBitmap.asImageBitmap(),
                        contentDescription = "Profile Picture",
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .border(2.dp, Color.White, CircleShape)
                            .clickable {
                                if (!isUploadingImage) {
                                    onShowImageDialog()
                                }
                            },
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "Default Profile",
                        modifier = Modifier
                            .size(120.dp)
                            .clickable {
                                if (!isUploadingImage) {
                                    onShowImageDialog()
                                }
                            },
                        tint = Color.LightGray
                    )
                }
                // Edit Image Button
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit Photo",
                    tint = if (isUploadingImage) Color.Gray else Color.White,
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(
                            if (isUploadingImage) Color.DarkGray.copy(alpha = 0.5f)
                            else Color.Black.copy(alpha = 0.5f)
                        )
                        .clickable {
                            if (!isUploadingImage) {
                                onEditImageClicked()
                            }
                        }
                        .padding(4.dp)
                )
            }

            // Show small loading indicator on profile image during upload
            if (isUploadingImage) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(30.dp),
                        strokeWidth = 3.dp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Admin Name
        Text(
            text = adminName,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = "Administrator",
            fontSize = 14.sp,
            color = Color.LightGray
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Admin Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = Color.White.copy(alpha = 0.1f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                InfoRow(icon = Icons.Default.Email, text = adminEmail)
                HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
                InfoRow(icon = Icons.Default.Phone, text = adminPhone)
                HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
                InfoRow(icon = Icons.Default.Cake, text = adminDob)
                HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
                InfoRow(icon = Icons.Default.VerifiedUser, text = "Administrator Account")
                HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (!isUploadingImage) {
                                onPasswordChangeClicked()
                            }
                        }
                        .padding(vertical = 8.dp)
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = "Password",
                        tint = if (isUploadingImage) Color.Gray else Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Password",
                            color = if (isUploadingImage) Color.Gray else Color.White
                        )
                        Text(
                            "Last updated: $passwordLastUpdated",
                            fontSize = 12.sp,
                            color = if (isUploadingImage) Color.DarkGray else Color.LightGray
                        )
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = "Change Password",
                        tint = if (isUploadingImage) Color.Gray else Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Logout Button
        Button(
            onClick = {
                if (!isUploadingImage) {
                    FirebaseAuth.getInstance().signOut()
                    val intent = Intent(context, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    context.startActivity(intent)
                    (context as? Activity)?.finish()
                }
            },
            enabled = !isUploadingImage,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Red.copy(alpha = if (isUploadingImage) 0.3f else 0.7f)
            )
        ) {
            Icon(
                Icons.Default.ExitToApp,
                contentDescription = "Logout",
                tint = if (isUploadingImage) Color.LightGray else Color.White
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Logout",
                color = if (isUploadingImage) Color.LightGray else Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun AdminContent(title: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                Icons.Default.Construction,
                contentDescription = "Under Construction",
                tint = Color.White,
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "$title",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "This section is under development",
                fontSize = 16.sp,
                color = Color.LightGray
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AdminPreview() {
    DemoTheme {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            AdminHomeContent("Admin Name")
        }
    }
}