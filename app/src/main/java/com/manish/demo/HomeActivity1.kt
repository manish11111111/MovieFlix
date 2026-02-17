package com.manish.demo

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.*

import androidx.compose.runtime.*
import androidx.compose.ui.viewinterop.AndroidView

import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.ui.viewinterop.AndroidView

import android.webkit.WebChromeClient.CustomViewCallback
import androidx.compose.foundation.BorderStroke
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.res.painterResource
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.statusBarsPadding

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll

import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable

import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.google.accompanist.swiperefresh.SwipeRefreshIndicator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.manish.demo.models.Movie
import com.manish.demo.ui.ChangePasswordDialog
import com.manish.demo.ui.InfoRow
import com.manish.demo.ui.components.CustomToastCompose
import com.manish.demo.ui.components.ImageSelectionDialog
import com.manish.demo.ui.theme.DemoTheme
import com.manish.demo.viewmodel.UserHomeViewModel
import com.manish.demo.viewmodel.MovieItem
import com.manish.demo.viewmodel.GenreSection
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.compareTo
import kotlin.div
import kotlin.or
import kotlin.text.compareTo
import kotlin.text.get
import kotlin.text.set
import kotlin.text.toInt
import kotlin.times

class HomeActivity1 : ComponentActivity() {
    private var userName by mutableStateOf("User")
    private var userEmail by mutableStateOf("")
    private var userPhone by mutableStateOf("")
    private var userDob by mutableStateOf("")
    private var userImageBitmap by mutableStateOf<Bitmap?>(null)
    private var passwordLastUpdated by mutableStateOf("Never")
    private var isRefreshing by mutableStateOf(false)
    private var showSuccessToast by mutableStateOf(false)
    private var isUploadingImage by mutableStateOf(false)

    // Movie state variables
    private var moviesList by mutableStateOf<List<Movie>>(emptyList())
    private var isLoadingMovies by mutableStateOf(false)
    private var featuredMovie by mutableStateOf<Movie?>(null)
    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            handleImageSelection(uri)
        }
    }

    // Activity result launchers

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission just granted, proceed to camera
            handleImageSourceSelection("camera_action")
        } else {
            // Check if permanently denied
            if (!ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.CAMERA)) {
                showSettingsDialog("Camera")
            }
        }
    }

    // ✅ 3. Settings Redirect Dialog
    private fun showSettingsDialog(permissionName: String) {
        if (!isFinishing) {
            android.app.AlertDialog.Builder(this)
                .setTitle("Permission Required")
                .setMessage("You have permanently denied $permissionName access. Please enable it in app settings to update your profile photo.")
                .setPositiveButton("Go to Settings") { _, _ ->
                    try {
                        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        val uri = Uri.fromParts("package", packageName, null)
                        intent.data = uri
                        startActivity(intent)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private val requestGalleryPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission just granted, proceed to gallery
            handleImageSourceSelection("gallery_action")
        } else {
            // Determine which permission was requested
            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                android.Manifest.permission.READ_MEDIA_IMAGES
            else
                android.Manifest.permission.READ_EXTERNAL_STORAGE

            // Check if permanently denied
            if (!ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                showSettingsDialog("Gallery/Storage")
            }
        }
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            handleCameraImage(bitmap)
            Log.d("HomeActivity1", "Camera image captured")
        } else {
            Log.d("HomeActivity1", "Camera launcher returned null")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Simple approach - avoid edge-to-edge complexities
        enableEdgeToEdge()

        // Set status bar to transparent
        window.statusBarColor = android.graphics.Color.TRANSPARENT

        Log.d("HomeActivity1", "Activity created")

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("HomeActivity1", "Uncaught exception in thread: ${thread.name}", throwable)
        }

        // CALL setContent ONLY ONCE HERE
        setContent {
            DemoTheme {
                UserApp(
                    userName = userName,
                    userEmail = userEmail,
                    userPhone = userPhone,
                    userDob = userDob,
                    userImageBitmap = userImageBitmap,
                    passwordLastUpdated = passwordLastUpdated,
                    isRefreshing = isRefreshing,
                    showSuccessToast = showSuccessToast,
                    isUploadingImage = isUploadingImage,
                    onRefresh = {
                        if (!isUploadingImage) {
                            isRefreshing = true
                            fetchUserData()
                        }
                    },
                    onHideSuccessToast = {
                        showSuccessToast = false
                    },
                    onImageSelected = { sourceType: String ->
                        handleImageSourceSelection(sourceType)
                    }
                )
            }
        }

        listenForBanStatus()
        listenForAccountChanges()
        fetchUserData()
    }

    private var currentRole: String? = null // To track the role locally

    private fun listenForAccountChanges() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            FirebaseFirestore.getInstance().collection("users").document(user.uid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) return@addSnapshotListener

                    if (snapshot != null && snapshot.exists()) {
                        val roleInDb = snapshot.getString("role") ?: "USER"
                        val isBanned = snapshot.getBoolean("isBanned") ?: false

                        // 1. Check for Ban (Already implemented, but good to keep together)
                        if (isBanned) {
                            showAccountDialog("Account Banned", "Your account has been banned. You will be logged out.")
                            return@addSnapshotListener
                        }

                        // 2. Check for Role Change
                        if (currentRole == null) {
                            currentRole = roleInDb // Set the initial role when app starts
                        } else if (!currentRole.equals(roleInDb, ignoreCase = true)) {
                            // If currentRole is "USER" and roleInDb becomes "admin"
                            showAccountDialog("Role Updated", "Your access level has changed. Please login again to access your new features.")
                        }
                    }
                }
        }
    }

    private fun showAccountDialog(title: String, message: String) {
        if (!isFinishing) {
            android.app.AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("OK") { _, _ ->
                    FirebaseAuth.getInstance().signOut()
                    val intent = Intent(this, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
                .show()
        }
    }

    private fun fetchUserData() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            FirebaseFirestore.getInstance().collection("users").document(userId)
                .get(Source.SERVER)
                .addOnSuccessListener { document ->
                    if (document != null) {
                        userName = document.getString("fullName") ?: "User"
                        userEmail = document.getString("email") ?: ""
                        userPhone = document.getString("phone") ?: ""
                        userDob = document.getString("dob") ?: ""

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
                                userImageBitmap = BitmapFactory.decodeByteArray(
                                    decodedBytes,
                                    0,
                                    decodedBytes.size
                                )
                                Log.d("HomeActivity1", "Profile image loaded from Firebase")
                            } catch (e: Exception) {
                                Log.e("HomeActivity1", "Error decoding image", e)
                            }
                        }
                    }
                    isRefreshing = false
                }
                .addOnFailureListener { exception ->
                    Log.e("HomeActivity1", "Error fetching user data", exception)
                    isRefreshing = false
                }
        } else {
            isRefreshing = false
        }
    }

    private fun listenForBanStatus() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            // Listen to the current user's document in Firestore
            FirebaseFirestore.getInstance().collection("users").document(user.uid)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) return@addSnapshotListener

                    if (snapshot != null && snapshot.exists()) {
                        val isBanned = snapshot.getBoolean("isBanned") ?: false

                        // If the admin switched isBanned to true, kick them out!
                        if (isBanned) {
                            showBanDialogAndLogout()
                        }
                    }
                }
        }
    }

    private fun showBanDialogAndLogout() {
        // Only show if the activity is not currently finishing
        if (!isFinishing) {
            val builder = android.app.AlertDialog.Builder(this)
            builder.setTitle("Account Banned")
            builder.setMessage("Your account has been banned by the administrator. You will be logged out now.")
            builder.setCancelable(false) // Force the user to click OK
            builder.setPositiveButton("OK") { _, _ ->
                // 1. Clear session
                FirebaseAuth.getInstance().signOut()

                // 2. Go to login and clear activity stack
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            builder.show()
        }
    }

    // ✅ 2. Updated Selection Logic
    private fun handleImageSourceSelection(sourceType: String) {
        when (sourceType) {
            "gallery" -> {
                // Determine permission type based on Android version
                val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    android.Manifest.permission.READ_MEDIA_IMAGES
                else
                    android.Manifest.permission.READ_EXTERNAL_STORAGE

                if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
                    galleryLauncher.launch("image/*")
                } else {
                    requestGalleryPermissionLauncher.launch(permission)
                }
            }
            "gallery_action" -> {
                // Internal signal: Permission was granted by launcher, now open gallery
                galleryLauncher.launch("image/*")
            }
            "camera" -> {
                if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    cameraLauncher.launch(null)
                } else {
                    requestCameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                }
            }
            "camera_action" -> {
                // Internal signal: Permission was granted by launcher, now open camera
                cameraLauncher.launch(null)
            }
        }
    }


    private fun openGallery() {
        try {
            Log.d("HomeActivity1", "Opening gallery")
            galleryLauncher.launch("image/*")
        } catch (e: Exception) {
            Log.e("HomeActivity1", "Error opening gallery", e)
        }
    }

    private fun openCamera() {
        try {
            Log.d("HomeActivity1", "Opening camera")
            cameraLauncher.launch(null)
        } catch (e: Exception) {
            Log.e("HomeActivity1", "Error opening camera", e)
        }
    }

    private fun handleImageSelection(imageUri: Uri) {
        isUploadingImage = true

        try {
            Log.d("HomeActivity1", "Processing image from URI: $imageUri")

            // First, get image dimensions without loading the full bitmap
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

            var inputStream: InputStream? = contentResolver.openInputStream(imageUri)
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()

            val imageWidth = options.outWidth
            val imageHeight = options.outHeight
            Log.d("HomeActivity1", "Original image size: ${imageWidth}x${imageHeight}")

            if (imageWidth <= 0 || imageHeight <= 0) {
                Log.e("HomeActivity1", "Invalid image dimensions")
                isUploadingImage = false
                return
            }

            // Calculate sample size to reduce memory usage
            val maxDimension = 1024 // Max width/height
            var sampleSize = 1
            if (imageWidth > maxDimension || imageHeight > maxDimension) {
                val widthRatio = imageWidth / maxDimension
                val heightRatio = imageHeight / maxDimension
                sampleSize = if (widthRatio > heightRatio) widthRatio else heightRatio
            }

            Log.d("HomeActivity1", "Using sample size: $sampleSize")

            // Now decode with sample size to reduce memory
            val decodingOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565 // Use less memory
            }

            inputStream = contentResolver.openInputStream(imageUri)
            val bitmap = BitmapFactory.decodeStream(inputStream, null, decodingOptions)
            inputStream?.close()

            if (bitmap != null) {
                Log.d("HomeActivity1", "Bitmap decoded successfully: ${bitmap.width}x${bitmap.height}")

                // Further scale if still too large
                val scaledBitmap = if (bitmap.width > 800 || bitmap.height > 800) {
                    val scale = 800f / Math.max(bitmap.width, bitmap.height)
                    val newWidth = (bitmap.width * scale).toInt()
                    val newHeight = (bitmap.height * scale).toInt()
                    Log.d("HomeActivity1", "Scaling to: ${newWidth}x${newHeight}")
                    val scaled = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
                    bitmap.recycle() // Free original bitmap memory
                    scaled
                } else {
                    bitmap
                }

                val base64Image = convertBitmapToBase64(scaledBitmap)
                updateProfileImageInFirebase(base64Image)
                scaledBitmap.recycle() // Free scaled bitmap memory

            } else {
                Log.e("HomeActivity1", "Failed to decode bitmap from gallery URI")
                isUploadingImage = false
            }

        } catch (e: OutOfMemoryError) {
            Log.e("HomeActivity1", "Out of memory while processing image", e)
            isUploadingImage = false
        } catch (e: Exception) {
            Log.e("HomeActivity1", "Error processing gallery image", e)
            isUploadingImage = false
        }
    }


    private fun handleCameraImage(bitmap: Bitmap) {
        isUploadingImage = true

        try {
            Log.d("HomeActivity1", "Camera image captured, size: ${bitmap.width}x${bitmap.height}")
            val base64Image = convertBitmapToBase64(bitmap)
            updateProfileImageInFirebase(base64Image)
        } catch (e: Exception) {
            Log.e("HomeActivity1", "Error processing camera image", e)
            isUploadingImage = false
        }
    }

    private fun convertBitmapToBase64(bitmap: Bitmap): String {
        val byteArrayOutputStream = ByteArrayOutputStream()
        // Use higher quality compression, the bitmap is already scaled down
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        Log.d("HomeActivity1", "Base64 size: ${byteArray.size / 1024}KB")
        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }




// 2. Ensure Toast shows and Data Refreshes
private fun updateProfileImageInFirebase(base64Image: String) {
    val userId = FirebaseAuth.getInstance().currentUser?.uid
    if (userId != null) {
        FirebaseFirestore.getInstance().collection("users").document(userId)
            .update("profileImage", base64Image)
            .addOnSuccessListener {
                Log.d("HomeActivity1", "Profile image updated successfully")

                // Stop loader
                isUploadingImage = false

                // Trigger Toast in Composable
                showSuccessToast = true

                // Refresh user data to show new image immediately
                fetchUserData()
            }
            .addOnFailureListener { e ->
                Log.e("HomeActivity1", "Error updating profile image", e)
                isUploadingImage = false
            }
    } else {
        isUploadingImage = false
    }
}

    private fun checkPermission(permission: String): Boolean {
        val result = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        Log.d("HomeActivity1", "checkPermission for $permission: $result")
        return result
    }

    private fun requestPermission(permission: String, requestCode: Int) {
        Log.d("HomeActivity1", "requestPermission for $permission with code $requestCode")
        ActivityCompat.requestPermissions(this, arrayOf(permission), requestCode)
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        @Suppress("DEPRECATION")
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        Log.d("HomeActivity1", "onRequestPermissionsResult called: requestCode=$requestCode, permissions=${permissions.joinToString()}")

        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d("HomeActivity1", "Permission granted for request code: $requestCode")
            when (requestCode) {
                100 -> {
                    openGallery()
                }
                101 -> {
                    openCamera()
                }
            }
        } else {
            Log.d("HomeActivity1", "Permission denied for request code: $requestCode")
        }
    }
}

// Add this enum class outside the HomeActivity1 class
enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
) {
    HOME("Home", Icons.Default.Home),
    FAVORITES("Favorites", Icons.Default.Favorite),
    PROFILE("Profile", Icons.Default.AccountCircle),
}

// Add the UserApp composable function
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserApp(
    userName: String,
    userEmail: String,
    userPhone: String,
    userDob: String,
    userImageBitmap: Bitmap?,
    passwordLastUpdated: String,
    isRefreshing: Boolean,
    showSuccessToast: Boolean,
    isUploadingImage: Boolean,
    onRefresh: () -> Unit,
    onHideSuccessToast: () -> Unit,
    onImageSelected: (String) -> Unit
) {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }

    // ✅ 1. State for View Image Dialog
    var showImageDialog by remember { mutableStateOf(false) }

    var showImageSelectionDialog by remember { mutableStateOf(false) }
    var showExitConfirmation by remember { mutableStateOf(false) }
    var passwordDialogOpen by remember { mutableStateOf(false) }

    var selectedMovie by remember { mutableStateOf<MovieItem?>(null) }
    var playingMovie by remember { mutableStateOf<MovieItem?>(null) }

    val context = LocalContext.current
    val activity = context as? Activity
    val localImageBitmap = userImageBitmap

    BackHandler(enabled = !showExitConfirmation && !passwordDialogOpen && !showImageDialog) {
        when {
            playingMovie != null -> playingMovie = null
            selectedMovie != null -> selectedMovie = null
            showImageDialog -> showImageDialog = false // ✅ Close image dialog on back
            currentDestination != AppDestinations.HOME -> currentDestination = AppDestinations.HOME
            else -> showExitConfirmation = true
        }
    }

    LaunchedEffect(showSuccessToast) {
        if (showSuccessToast) {
            kotlinx.coroutines.delay(2000)
            onHideSuccessToast()
        }
    }

    val gradient = Brush.linearGradient(
        colors = listOf(Color(0xFF450457), Color(0xFF120017), Color(0xFF000000))
    )

    Box(modifier = Modifier.fillMaxSize().background(gradient)) {
        CustomToastCompose(
            message = "Picture updated successfully",
            showToast = showSuccessToast,
            onDismiss = onHideSuccessToast
        )

        if (isUploadingImage) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Text("Uploading image...", color = Color.White, modifier = Modifier.padding(top = 16.dp))
                }
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_logo_m),
                                contentDescription = "Logo",
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("MovieFlix", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        }
                    },
                    actions = {
                        if (currentDestination == AppDestinations.HOME) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.End,
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text(
                                    text = userName.split(" ").firstOrNull() ?: userName,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                if (localImageBitmap != null) {
                                    Image(
                                        bitmap = localImageBitmap.asImageBitmap(),
                                        contentDescription = "User Profile",
                                        modifier = Modifier.size(36.dp).clip(CircleShape).border(1.5.dp, Color(0xFF2ECC71), CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(Icons.Default.AccountCircle, "User Profile", tint = Color.White, modifier = Modifier.size(36.dp))
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = Color.Transparent,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.3f))
                ) {
                    AppDestinations.entries.forEach { item ->
                        val isFav = item == AppDestinations.FAVORITES
                        val selectedIconColor = if (isFav) Color.Red else Color.White
                        val unselectedIconColor = if (isFav) Color.Red.copy(0.6f) else Color.LightGray

                        NavigationBarItem(
                            icon = {
                                if (item == AppDestinations.PROFILE && userImageBitmap != null) {
                                    Image(
                                        bitmap = userImageBitmap.asImageBitmap(),
                                        contentDescription = item.label,
                                        modifier = Modifier.size(26.dp).clip(CircleShape).border(1.dp, if (item == currentDestination) Color.White else Color.Gray, CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(item.icon, contentDescription = item.label)
                                }
                            },
                            label = { Text(item.label) },
                            selected = item == currentDestination,
                            onClick = { if (!isUploadingImage) currentDestination = item },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = selectedIconColor,
                                selectedTextColor = Color.White,
                                unselectedIconColor = unselectedIconColor,
                                unselectedTextColor = Color.LightGray,
                                indicatorColor = selectedIconColor.copy(alpha = 0.2f)
                            )
                        )
                    }
                }
            },
            containerColor = Color.Transparent
        ) { innerPadding ->
            if (!isUploadingImage) {
                SwipeRefresh(
                    state = rememberSwipeRefreshState(isRefreshing = isRefreshing),
                    onRefresh = onRefresh,
                    indicator = { state, trigger -> SwipeRefreshIndicator(state, trigger, backgroundColor = Color.Transparent, contentColor = Color.White) },
                    modifier = Modifier.fillMaxSize().padding(innerPadding)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        when (currentDestination) {
                            AppDestinations.HOME -> UserHomeContent(
                                userName = userName,
                                onMovieClick = { selectedMovie = it },
                                onNavigateToFavorites = { currentDestination = AppDestinations.FAVORITES },
                                modifier = Modifier.fillMaxSize()
                            )
                            AppDestinations.FAVORITES -> UserFavoritesContent(
                                onMovieClick = { selectedMovie = it },
                                modifier = Modifier.fillMaxSize()
                            )
                            AppDestinations.PROFILE -> UserProfileContent(
                                userName = userName,
                                userEmail = userEmail,
                                userPhone = userPhone,
                                userDob = userDob,
                                userImageBitmap = localImageBitmap,
                                passwordLastUpdated = passwordLastUpdated,
                                isUploadingImage = isUploadingImage,
                                // ✅ 2. RESTORED CLICK HANDLER
                                onShowImageDialog = { showImageDialog = true },
                                onEditImageClicked = { showImageSelectionDialog = true },
                                onPasswordChangeClicked = { passwordDialogOpen = true },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    when (currentDestination) {
                        AppDestinations.HOME -> UserHomeContent(
                            userName = userName,
                            onMovieClick = { selectedMovie = it },
                            onNavigateToFavorites = { currentDestination = AppDestinations.FAVORITES },
                            modifier = Modifier.fillMaxSize()
                        )
                        AppDestinations.FAVORITES -> UserFavoritesContent(
                            onMovieClick = { selectedMovie = it },
                            modifier = Modifier.fillMaxSize()
                        )
                        AppDestinations.PROFILE -> UserProfileContent(
                            userName = userName,
                            userEmail = userEmail,
                            userPhone = userPhone,
                            userDob = userDob,
                            userImageBitmap = localImageBitmap,
                            passwordLastUpdated = passwordLastUpdated,
                            isUploadingImage = isUploadingImage,
                            // ✅ 3. RESTORED CLICK HANDLER (Non-refreshable)
                            onShowImageDialog = { showImageDialog = true },
                            onEditImageClicked = { showImageSelectionDialog = true },
                            onPasswordChangeClicked = { passwordDialogOpen = true },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        // Overlays
        selectedMovie?.let { movie ->
            MovieDetailScreen(movie = movie, onClose = { selectedMovie = null }, onPlayClick = { playingMovie = it })
        }
        playingMovie?.let { movie ->
            MoviePlayerScreen(movie = movie, onClose = { playingMovie = null })
        }

        // ✅ 4. Full Screen Profile Image Dialog
        // ✅ 4. Full Screen Profile Image Dialog with Blur & Tap-to-Dismiss
        if (showImageDialog && localImageBitmap != null) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showImageDialog = false },
                properties = androidx.compose.ui.window.DialogProperties(
                    usePlatformDefaultWidth = false, // Use full screen width
                    decorFitsSystemWindows = false
                )
            ) {
                // Outer Box: The "Blurred" Background covering the whole screen
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.85f)) // Strong dim for focus effect
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null, // No ripple on background tap
                            onClick = { showImageDialog = false } // Close on outside tap
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // The Profile Image
                    // We disable click on the image itself so it doesn't close when tapping the face
                    Image(
                        bitmap = localImageBitmap.asImageBitmap(),
                        contentDescription = "Full Size Profile",
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .border(3.dp, Color.White, CircleShape)
                            .clickable(enabled = false) {}, // Consume clicks on image
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }


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
                onPasswordChanged = { _ -> passwordDialogOpen = false; onRefresh() }
            )
        }
    }
}






@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserHomeContent(
    userName: String,
    onMovieClick: (MovieItem) -> Unit,
    onNavigateToFavorites: () -> Unit, // ✅ Added Param
    modifier: Modifier = Modifier
) {
    val viewModel: UserHomeViewModel = viewModel()
    val topRatedMovies by viewModel.topRatedMovies.collectAsState()
    val newlyAddedMovies by viewModel.newlyAddedMovies.collectAsState()
    val genreSections by viewModel.genreSections.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isLoadingMovies by viewModel.isLoading.collectAsState()
    val userStats by viewModel.userStats.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    val emeraldGreen = Color(0xFF2ECC71)
    val glassBg = Color.White.copy(alpha = 0.05f)
    val glassBorder = Color.White.copy(alpha = 0.1f)

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            // Search Bar Loop (Always visible)
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it; viewModel.searchMovies(it) },
                        modifier = Modifier.fillMaxWidth().background(glassBg, RoundedCornerShape(12.dp)).border(1.dp, glassBorder, RoundedCornerShape(12.dp)),
                        placeholder = { Text("Search movies...", color = Color.White.copy(0.5f)) },
                        leadingIcon = { Icon(Icons.Default.Search, "Search", tint = emeraldGreen) },
                        trailingIcon = { if (searchQuery.isNotEmpty()) { IconButton(onClick = { searchQuery = ""; viewModel.clearSearch() }) { Icon(Icons.Default.Close, "Clear", tint = Color.White) } } },
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = emeraldGreen,
                            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = emeraldGreen, unfocusedIndicatorColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(12.dp), singleLine = true
                    )
                }
            }

            if (searchQuery.isEmpty()) {
                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp)) {
                        Text("Welcome back,", color = Color.White.copy(0.6f), fontSize = 14.sp)
                        Text(userName, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(20.dp))

                        // Stats Cards
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            UserStatCard(
                                modifier = Modifier.weight(1f),
                                title = "Subscription",
                                value = userStats.subscriptionStatus,
                                icon = Icons.Default.CardMembership,
                                color = if (userStats.subscriptionStatus == "Active") emeraldGreen else Color.Red
                            )
                            UserStatCard(
                                modifier = Modifier.weight(1f),
                                title = "Watched",
                                value = "${userStats.watchCount}",
                                icon = Icons.Default.PlayCircle,
                                color = Color.Cyan
                            )
                            UserStatCard(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onNavigateToFavorites() }, // ✅ Navigate on click
                                title = "Favorites",
                                value = "${userStats.favoritesCount}",
                                icon = Icons.Default.Favorite,
                                color = Color.Magenta
                            )
                        }
                    }
                }
            }

            if (searchQuery.isNotEmpty()) {
                if (searchResults.isNotEmpty()) {
                    item { Text("Search Results (${searchResults.size})", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
                    item {
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(searchResults) { movie -> NetflixMovieCard(movie = movie, onClick = { onMovieClick(movie) }) }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                } else {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().height(400.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.SearchOff, null, tint = Color.White.copy(0.3f), modifier = Modifier.size(80.dp))
                                Spacer(Modifier.height(16.dp))
                                Text("No match found", color = Color.White.copy(0.6f), fontSize = 20.sp)
                            }
                        }
                    }
                }
            } else {
                if (topRatedMovies.isNotEmpty()) item { MovieSection("⭐ Top Rated", topRatedMovies, onMovieClick) }
                if (newlyAddedMovies.isNotEmpty()) item { MovieSection("🆕 Newly Added", newlyAddedMovies, onMovieClick) }
                items(genreSections) { genre -> MovieSection(genre.name, genre.movies, onMovieClick) }
                if (isLoadingMovies) item { Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = emeraldGreen) } }
            }
        }
    }
}



@Composable
fun UserStatCard(
    modifier: Modifier,
    title: String,
    value: String,
    icon: ImageVector,
    color: Color
) {
    val glassBg = Color.White.copy(alpha = 0.05f)
    val glassBorder = Color.White.copy(alpha = 0.1f)

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = glassBg),
        border = BorderStroke(1.dp, glassBorder),
        shape = RoundedCornerShape(16.dp) // Slightly smaller rounding for a tighter look
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp) // Reduced padding from 16.dp to 12.dp for smaller height
                .fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp) // Reduced icon box size from 32.dp
                    .background(color.copy(0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, Modifier.size(16.dp), color) // Slightly smaller icon
            }

            Spacer(Modifier.height(8.dp)) // Reduced spacer

            // Value Text (e.g., "Inactive", "12")
            Text(
                text = value,
                color = Color.White,
                fontSize = 16.sp, // Reduced from 20.sp to fit "Inactive" better
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Title Text (e.g., "Subscription")
            Text(
                text = title,
                color = Color.White.copy(0.5f),
                fontSize = 10.sp // Reduced slightly from 11.sp
            )
        }
    }
}



@Composable
fun MovieSection(
    title: String,
    movies: List<MovieItem>,
    onMovieClick: (MovieItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(movies) { movie ->
                NetflixMovieCard(
                    movie = movie,
                    onClick = { onMovieClick(movie) }
                )
            }
        }
    }
}

@Composable
fun NetflixMovieCard(
    movie: MovieItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(140.dp)
            .height(200.dp)
            .clickable { onClick() }
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.05f)
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Movie Poster
            if (movie.poster.isNotEmpty()) {
                SubcomposeAsyncImage(
                    model = movie.poster,
                    contentDescription = movie.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    loading = {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    },
                    error = {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.DarkGray),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Movie,
                                contentDescription = "No Poster",
                                tint = Color.LightGray,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.DarkGray),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Movie,
                        contentDescription = "No Poster",
                        tint = Color.LightGray,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            // Rating Badge
            if (movie.rating > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = "Rating",
                            tint = Color.Yellow,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            String.format(Locale.US, "%.1f", movie.rating),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Title Overlay
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.8f)
                            )
                        )
                    )
                    .padding(8.dp)
            ) {
                Text(
                    text = movie.title,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun UserFavoritesContent(
    onMovieClick: (MovieItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val db = FirebaseFirestore.getInstance()
    val userId = FirebaseAuth.getInstance().currentUser?.uid
    var favorites by remember { mutableStateOf<List<MovieItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // ✅ Search state for favorites
    var searchQuery by remember { mutableStateOf("") }
    val emeraldGreen = Color(0xFF2ECC71)
    val glassBg = Color.White.copy(alpha = 0.05f)
    val glassBorder = Color.White.copy(alpha = 0.1f)

    LaunchedEffect(userId) {
        if (userId != null) {
            db.collection("users").document(userId)
                .collection("favorites")
                .orderBy("addedAt", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        isLoading = false
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        favorites = snapshot.documents.mapNotNull { doc ->
                            try {
                                MovieItem(
                                    docId = doc.getString("movieId") ?: "",
                                    title = doc.getString("title") ?: "",
                                    poster = doc.getString("poster") ?: "",
                                    rating = (doc.get("rating") as? Number)?.toDouble() ?: 0.0
                                )
                            } catch (e: Exception) { null }
                        }
                    }
                    isLoading = false
                }
        } else {
            isLoading = false
        }
    }

    // ✅ Filter favorites based on search query
    val filteredFavorites = if (searchQuery.isEmpty()) {
        favorites
    } else {
        favorites.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ✅ Spacer for TopAppBar (Logo + MovieFlix)
            // The TopAppBar in UserApp is transparent and overlays content, so we need padding


            // ✅ Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 0.dp)
                    .background(glassBg, RoundedCornerShape(12.dp))
                    .border(1.dp, glassBorder, RoundedCornerShape(12.dp)),
                placeholder = { Text("Search favorites...", color = Color.White.copy(0.5f)) },
                leadingIcon = { Icon(Icons.Default.Search, "Search", tint = emeraldGreen) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, "Clear", tint = Color.White)
                        }
                    }
                },
                colors = TextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = emeraldGreen,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = emeraldGreen,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            // ✅ Title "Your Favorites"
            Text(
                text = "Your Favorites (${filteredFavorites.size})", // Changed text
                fontSize = 20.sp, // Reduced font size (was 24.sp)
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = emeraldGreen)
                }
            } else if (favorites.isEmpty()) {
                // Empty state (no favorites at all)
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.FavoriteBorder, null, tint = Color.White.copy(0.5f), modifier = Modifier.size(80.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("No favorites yet", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            } else if (filteredFavorites.isEmpty()) {
                // Search result empty
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No matches found", color = Color.White.copy(0.6f))
                }
            } else {
                // Grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredFavorites) { movie ->
                        NetflixMovieCard(
                            movie = movie,
                            onClick = {
                                // Fetch full details logic (kept same as before)
                                db.collection("movies").document(movie.docId).get().addOnSuccessListener { doc ->
                                    if (doc.exists()) {
                                        val full = MovieItem(
                                            docId = doc.id,
                                            tmdbId = (doc.get("tmdbId") as? Number)?.toInt() ?: 0,
                                            title = doc.getString("title") ?: "",
                                            description = doc.getString("description") ?: "",
                                            poster = doc.getString("poster") ?: "",
                                            backdrop = doc.getString("backdrop") ?: "",
                                            streamUrl = doc.getString("streamUrl") ?: "",
                                            trailerUrl = doc.getString("trailerUrl") ?: "",
                                            genres = (doc.get("genres") as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                                            rating = (doc.get("rating") as? Number)?.toDouble() ?: 0.0
                                        )
                                        onMovieClick(full)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}



@Composable
fun UserProfileContent(
    userName: String,
    userEmail: String,
    userPhone: String,
    userDob: String,
    userImageBitmap: Bitmap?,
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
                if (userImageBitmap != null) {
                    Image(
                        bitmap = userImageBitmap.asImageBitmap(),
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

        Text(
            text = userName,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = "MovieFlix User",
            fontSize = 14.sp,
            color = Color.LightGray
        )

        Spacer(modifier = Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = Color.White.copy(alpha = 0.1f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                InfoRow(icon = Icons.Default.Email, label = "Email", text = userEmail)
                HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
                InfoRow(icon = Icons.Default.Phone, label = "Phone", text = userPhone)
                HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
                InfoRow(icon = Icons.Default.Cake, label = "Date of Birth", text = userDob)
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
                Icons.AutoMirrored.Filled.ExitToApp,
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
fun MovieDetailScreen(
    movie: MovieItem,
    onClose: () -> Unit,
    onPlayClick: (MovieItem) -> Unit
) {
    val emeraldGreen = Color(0xFF2ECC71)
    val db = FirebaseFirestore.getInstance()
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    val context = LocalContext.current

    var isFavorite by remember { mutableStateOf(false) }
    var isCheckingFavorite by remember { mutableStateOf(true) }

    // Check if movie is in favorites
    LaunchedEffect(movie.docId) {
        if (currentUserId != null) {
            db.collection("users").document(currentUserId)
                .collection("favorites")
                .document(movie.docId)
                .get()
                .addOnSuccessListener { doc ->
                    isFavorite = doc.exists()
                    isCheckingFavorite = false
                }
                .addOnFailureListener {
                    isCheckingFavorite = false
                }
        } else {
            isCheckingFavorite = false
        }
    }

    // Toggle favorite function
    // Updated Toggle favorite function
    fun toggleFavorite() {
        if (currentUserId == null) return

        // Reference to the specific user's favorites sub-collection
        val favRef = db.collection("users").document(currentUserId)
            .collection("favorites")
            .document(movie.docId) // Use movie ID as document ID to ensure uniqueness per user

        if (isFavorite) {
            // Remove from favorites
            favRef.delete()
                .addOnSuccessListener {
                    isFavorite = false
                    Log.d("Favorites", "Movie removed from favorites")
                }
                .addOnFailureListener { e ->
                    Log.e("Favorites", "Error removing favorite", e)
                }
        } else {
            // Add to favorites
            // We store essential details to display lists without fetching the full movie again
            val favoriteData = hashMapOf(
                "userId" to currentUserId, // Relational link back to user (explicit)
                "movieId" to movie.docId,  // Relational link to the movie
                "tmdbId" to movie.tmdbId,
                "title" to movie.title,
                "poster" to movie.poster,
                "rating" to movie.rating,  // Useful for sorting
                "addedAt" to com.google.firebase.Timestamp.now()
            )

            favRef.set(favoriteData)
                .addOnSuccessListener {
                    isFavorite = true
                    Log.d("Favorites", "Movie added to favorites")
                }
                .addOnFailureListener { e ->
                    Log.e("Favorites", "Error adding favorite", e)
                }
        }
    }


    // Function to open YouTube trailer
    fun openTrailer() {
        if (movie.trailerUrl.isEmpty()) return

        try {
            // Construct full YouTube URL
            val videoId = movie.trailerUrl

            // Try to open in YouTube app first
            val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$videoId"))
            appIntent.setPackage("com.google.android.youtube")

            // Check if YouTube app is installed
            val packageManager = context.packageManager
            val youtubeAppInstalled = appIntent.resolveActivity(packageManager) != null

            if (youtubeAppInstalled) {
                // Open in YouTube app
                context.startActivity(appIntent)
            } else {
                // Fallback: Open in web browser
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$videoId"))
                context.startActivity(webIntent)
            }
        } catch (e: Exception) {
            Log.e("MovieDetail", "Error opening trailer: ${e.message}")
            // Last fallback: just open YouTube website
            try {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=${movie.trailerUrl}"))
                context.startActivity(webIntent)
            } catch (ex: Exception) {
                Log.e("MovieDetail", "Failed to open trailer: ${ex.message}")
            }
        }
    }

    // Function to share movie
    fun shareMovie() {
        try {
            // Build share message
            val shareMessage = buildString {
                append("🎬 Check out this movie on MovieFlix!\n\n")
                append("${movie.title}\n")

                if (movie.rating > 0) {
                    append("⭐ Rating: ${String.format(Locale.US, "%.1f", movie.rating)}/10\n")
                }

                if (movie.genres.isNotEmpty()) {
                    append("🎭 Genres: ${movie.genres.joinToString(", ")}\n")
                }

                append("\n${movie.description}\n")

                if (movie.trailerUrl.isNotEmpty()) {
                    append("\n🎥 Watch Trailer: https://www.youtube.com/watch?v=${movie.trailerUrl}\n")
                }

                append("\n📱 Download MovieFlix to watch now!")
            }

            // Create share intent
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Check out ${movie.title} on MovieFlix")
                putExtra(Intent.EXTRA_TEXT, shareMessage)
            }

            // Create chooser to let user pick app
            val chooserIntent = Intent.createChooser(shareIntent, "Share ${movie.title} via")
            context.startActivity(chooserIntent)

        } catch (e: Exception) {
            Log.e("MovieDetail", "Error sharing movie: ${e.message}")
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                // Movie Backdrop/Poster Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                ) {
                    // Backdrop Image
                    if (movie.backdrop.isNotEmpty()) {
                        SubcomposeAsyncImage(
                            model = movie.backdrop,
                            contentDescription = movie.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            loading = {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = emeraldGreen)
                                }
                            },
                            error = {
                                // Fallback to poster if backdrop fails
                                if (movie.poster.isNotEmpty()) {
                                    SubcomposeAsyncImage(
                                        model = movie.poster,
                                        contentDescription = movie.title,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                        )
                    } else if (movie.poster.isNotEmpty()) {
                        SubcomposeAsyncImage(
                            model = movie.poster,
                            contentDescription = movie.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    // Gradient overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.3f),
                                        Color.Black.copy(alpha = 0.8f),
                                        Color.Black
                                    )
                                )
                            )
                    )

                    // Close Button (X) at top-right
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .size(36.dp)
                            .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Movie Title and Info at bottom
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text(
                            text = movie.title,
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 28.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Rating
                        if (movie.rating > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = "Rating",
                                    tint = Color.Yellow,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    String.format(Locale.US, "%.1f", movie.rating),
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    " / 10",
                                    color = Color.White.copy(0.7f),
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    // Play Button
                    Button(
                        onClick = { onPlayClick(movie)},
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = emeraldGreen
                        ),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            modifier = Modifier.size(24.dp),
                            tint = Color.Black
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Play",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }

                    // Watch Trailer Button (directly below Play button)
                    if (movie.trailerUrl.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { openTrailer() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White
                            ),
                            border = BorderStroke(1.5.dp, Color.White.copy(0.5f)),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "Watch Trailer",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Watch Trailer",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Action Buttons Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Favorite (Heart Icon)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { toggleFavorite() }
                        ) {
                            Icon(
                                if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (isFavorite) Color.Red else Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Favorite",
                                color = Color.White,
                                fontSize = 11.sp
                            )
                        }

                        // Share
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { shareMovie() }
                        ) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "Share",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Share",
                                color = Color.White,
                                fontSize = 11.sp
                            )
                        }

                        // Download
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { /* TODO: Download */ }
                        ) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = "Download",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Download",
                                color = Color.White,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Description
                    Text(
                        text = movie.description,
                        color = Color.White.copy(0.9f),
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Genres
                    if (movie.genres.isNotEmpty()) {
                        Text(
                            "Genres",
                            color = Color.White.copy(0.7f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(movie.genres) { genre ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color.White.copy(0.12f),
                                    border = BorderStroke(1.dp, Color.White.copy(0.3f))
                                ) {
                                    Text(
                                        genre,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}



@Composable
fun MoviePlayerScreen(
    movie: com.manish.demo.viewmodel.MovieItem,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    var isLoading by remember { mutableStateOf(true) }
    var customView by remember { mutableStateOf<View?>(null) }

    // ✅ 1. MANAGE ORIENTATION & IMMERSIVE MODE (STATUS BAR HIDING)
    DisposableEffect(Unit) {
        val window = activity?.window
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)

            // Force Landscape
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

            // Hide System Bars (Status & Navigation)
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        }

        onDispose {
            val window = activity?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)

                // Reset Orientation to portrait/default
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

                // Show System Bars again
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // ✅ 2. THE MAIN WEBVIEW
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        databaseEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

                        // Block popups at system level
                        javaScriptCanOpenWindowsAutomatically = false
                        setSupportMultipleWindows(false)

                        userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"
                    }

                    webViewClient = object : WebViewClient() {
                        // Block Ad Resource Loading
                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            val url = request?.url.toString()
                            val adDomains = listOf(
                                "1xbet", "doubleclick", "adservice", "popads", "adsystem",
                                "google-analytics", "histats", "cloudevent", "bet", "casino"
                            )
                            if (adDomains.any { url.contains(it) }) {
                                return WebResourceResponse("text/plain", "utf-8", null)
                            }
                            return super.shouldInterceptRequest(view, request)
                        }

                        // Prevent Redirects to Ad pages
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val url = request?.url.toString()
                            return if (url.contains("vidsrc") || url.contains("embed") || url == movie.streamUrl) {
                                false
                            } else {
                                true // Block everything else
                            }
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isLoading = false

                            view?.evaluateJavascript("""
        (function() {
            window.open = function() { return null; };

            let lastTap = 0;
            let tapTimeout;

            function findVideo() {
                // Try to find video in main document or any same-origin iframes
                let v = document.querySelector('video');
                if (!v) {
                    const iframes = document.querySelectorAll('iframe');
                    for (let f of iframes) {
                        try {
                            v = f.contentWindow.document.querySelector('video');
                            if (v) break;
                        } catch (e) {}
                    }
                }
                return v;
            }

            function handleTaps(e) {
                const now = Date.now();
                const DOUBLE_TAP_DELAY = 300;
                const video = findVideo();
                if (!video) return;

                if (now - lastTap < DOUBLE_TAP_DELAY) {
                    // --- DOUBLE TAP DETECTED ---
                    clearTimeout(tapTimeout); // Prevent the single tap action
                    e.preventDefault();
                    e.stopImmediatePropagation();

                    const rect = e.target.getBoundingClientRect();
                    const x = e.clientX - rect.left;
                    const width = rect.width;

                    if (x < width / 2) {
                        // Left side: Backward 10s
                        video.currentTime = Math.max(0, video.currentTime - 10);
                        showVisualFeedback("-10s", e.clientX, e.clientY);
                    } else {
                        // Right side: Forward 10s
                        video.currentTime = Math.min(video.duration, video.currentTime + 10);
                        showVisualFeedback("+10s", e.clientX, e.clientY);
                    }
                } else {
                    // --- SINGLE TAP (Potential) ---
                    // We wait to see if a second tap follows
                    tapTimeout = setTimeout(() => {
                        // If you want to ALLOW pausing on single tap, do nothing here.
                        // If you want to BLOCK pausing on single tap, we would need to 
                        // intercept the player's own listeners, which is very difficult.
                    }, DOUBLE_TAP_DELAY);
                }
                lastTap = now;
            }

            // Simple visual indicator for seeking
            function showVisualFeedback(text, x, y) {
                const div = document.createElement('div');
                div.innerText = text;
                div.style.position = 'fixed';
                div.style.left = x + 'px';
                div.style.top = y + 'px';
                div.style.color = 'white';
                div.style.fontSize = '24px';
                div.style.fontWeight = 'bold';
                div.style.zIndex = '999999';
                div.style.pointerEvents = 'none';
                div.style.backgroundColor = 'rgba(0,0,0,0.5)';
                div.style.padding = '10px';
                div.style.borderRadius = '50%';
                document.body.appendChild(div);
                setTimeout(() => div.remove(), 500);
            }

            // Monitor for clicks on the window/video
            document.addEventListener('click', handleTaps, true);

            // Existing Ad-Blocker Logic
            function removeAds() {
                const adSelectors = ['.ad-container', '.overlay', '#pop-ad', '[id*="pop"]', '[class*="ad-"]'];
                adSelectors.forEach(s => {
                    document.querySelectorAll(s).forEach(el => el.remove());
                });
            }
            setInterval(removeAds, 1000);
        })();
    """.trimIndent(), null)
                        }
                    }

                    webChromeClient = object : WebChromeClient() {
                        // Handle Fullscreen Video Signal
                        override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                            customView = view
                        }
                        override fun onHideCustomView() {
                            customView = null
                        }
                    }

                    val headers = HashMap<String, String>()
                    headers["Referer"] = "https://vidsrc.to/"
                    loadUrl(movie.streamUrl, headers)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // ✅ 4. FULLSCREEN VIDEO OVERLAY (The actual movie rendering)
        if (customView != null) {
            AndroidView(
                factory = { ctx ->
                    FrameLayout(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setBackgroundColor(android.graphics.Color.BLACK)
                        (customView?.parent as? ViewGroup)?.removeView(customView)
                        addView(customView)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // ✅ 5. UI OVERLAYS (Close button & Loader)
        // We only show these if the video isn't currently in "Custom Fullscreen"
        if (customView == null) {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF2ECC71))
                }
            }

            // Close Button (Positioned at Top-Right for Landscape)
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(24.dp) // Added more padding for easier clicking in landscape
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }
    }
}



