package com.manish.demo

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.google.accompanist.swiperefresh.SwipeRefreshIndicator
import com.google.firebase.Timestamp
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
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

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

    // Activity result launchers
    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            handleImageSelection(uri, "gallery")
            Log.d("HomeActivity1", "Gallery image selected: $uri")
        } else {
            Log.d("HomeActivity1", "Gallery launcher returned null")
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
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        // Set status bar to transparent
        window.statusBarColor = android.graphics.Color.TRANSPARENT

        Log.d("HomeActivity1", "Activity created")

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("HomeActivity1", "Uncaught exception in thread: ${thread.name}", throwable)
        }

        fetchUserData()
        fetchMovies()
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
                    updateUI()
                }
                .addOnFailureListener { exception ->
                    Log.e("HomeActivity1", "Error fetching user data", exception)
                    isRefreshing = false
                    updateUI()
                }
        } else {
            isRefreshing = false
            updateUI()
        }
    }

    private fun fetchMovies() {
        isLoadingMovies = true

        // First fetch featured movie (latest release)
        FirebaseFirestore.getInstance()
            .collection("movies")
            .orderBy("releaseYear", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { featuredDoc ->
                if (!featuredDoc.isEmpty && featuredDoc.documents.isNotEmpty()) {
                    featuredMovie = featuredDoc.documents[0].toObject(Movie::class.java)
                    Log.d("HomeActivity1", "Featured movie set: ${featuredMovie?.title}")
                }

                // Then fetch all movies
                FirebaseFirestore.getInstance()
                    .collection("movies")
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(50)
                    .get()
                    .addOnSuccessListener { documents ->
                        val movies = mutableListOf<Movie>()
                        for (document in documents) {
                            val movie = document.toObject(Movie::class.java)
                            movie?.let { movies.add(it) }
                        }
                        moviesList = movies
                        isLoadingMovies = false
                        Log.d("HomeActivity1", "Fetched ${movies.size} movies")
                    }
                    .addOnFailureListener { exception ->
                        Log.e("HomeActivity1", "Error fetching movies", exception)
                        isLoadingMovies = false
                    }
            }
            .addOnFailureListener { exception ->
                Log.e("HomeActivity1", "Error fetching featured movie", exception)
                isLoadingMovies = false
            }
    }

    private fun updateUI() {
        try {
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
                        moviesList = moviesList,
                        isLoadingMovies = isLoadingMovies,
                        featuredMovie = featuredMovie,
                        onRefresh = {
                            if (!isUploadingImage) {
                                isRefreshing = true
                                fetchUserData()
                                fetchMovies()
                            }
                        },
                        onHideSuccessToast = {
                            showSuccessToast = false
                        },
                        onImageSelected = { sourceType ->
                            // Fix: Call the correct function
                            handleImageSourceSelection(sourceType)
                        },
                        onMovieClicked = { movie ->
                            // Handle movie click
                            Log.d("HomeActivity1", "Movie clicked: ${movie.title}")
                        }
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("HomeActivity1", "Error in setContent", e)
        }
    }

    private fun handleImageSourceSelection(sourceType: String) {
        Log.d("HomeActivity1", "handleImageSourceSelection called with: $sourceType")

        when (sourceType) {
            "gallery" -> {
                Log.d("HomeActivity1", "Gallery selected")
                try {
                    openGallery()
                } catch (e: SecurityException) {
                    Log.w("HomeActivity1", "SecurityException, requesting permission")
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        val permission = android.Manifest.permission.READ_EXTERNAL_STORAGE
                        if (checkPermission(permission)) {
                            openGallery()
                        } else {
                            requestPermission(permission, 100)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("HomeActivity1", "Error in openGallery", e)
                }
            }
            "camera" -> {
                Log.d("HomeActivity1", "Camera selected")
                if (checkPermission(android.Manifest.permission.CAMERA)) {
                    Log.d("HomeActivity1", "Camera permission granted")
                    openCamera()
                } else {
                    Log.d("HomeActivity1", "Requesting CAMERA permission")
                    requestPermission(android.Manifest.permission.CAMERA, 101)
                }
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

    private fun handleImageSelection(imageUri: Uri, sourceType: String) {
        isUploadingImage = true
        updateUI()

        try {
            Log.d("HomeActivity1", "handleImageSelection called with URI: $imageUri")
            val inputStream: InputStream? = contentResolver.openInputStream(imageUri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmap != null) {
                Log.d("HomeActivity1", "Bitmap decoded successfully, size: ${bitmap.width}x${bitmap.height}")
                val base64Image = convertBitmapToBase64(bitmap)
                updateProfileImageInFirebase(base64Image)
            } else {
                Log.e("HomeActivity1", "Bitmap is null after decoding")
                isUploadingImage = false
                updateUI()
            }
        } catch (e: Exception) {
            Log.e("HomeActivity1", "Error processing image", e)
            isUploadingImage = false
            updateUI()
        }
    }

    private fun handleCameraImage(bitmap: Bitmap) {
        isUploadingImage = true
        updateUI()

        try {
            Log.d("HomeActivity1", "Camera image captured, size: ${bitmap.width}x${bitmap.height}")
            val base64Image = convertBitmapToBase64(bitmap)
            updateProfileImageInFirebase(base64Image)
        } catch (e: Exception) {
            Log.e("HomeActivity1", "Error processing camera image", e)
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
                    Log.d("HomeActivity1", "Profile image updated successfully")
                    showSuccessToast = true
                    isUploadingImage = false
                    fetchUserData()
                }
                .addOnFailureListener { e ->
                    Log.e("HomeActivity1", "Error updating profile image", e)
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
        Log.d("HomeActivity1", "checkPermission for $permission: $result")
        return result
    }

    private fun requestPermission(permission: String, requestCode: Int) {
        Log.d("HomeActivity1", "requestPermission for $permission with code $requestCode")
        ActivityCompat.requestPermissions(this, arrayOf(permission), requestCode)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
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
    moviesList: List<Movie>,
    isLoadingMovies: Boolean,
    featuredMovie: Movie?,
    onRefresh: () -> Unit,
    onHideSuccessToast: () -> Unit,
    onImageSelected: (String) -> Unit,
    onMovieClicked: (Movie) -> Unit
) {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }
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

    var localImageBitmap by remember { mutableStateOf(userImageBitmap) }

    // --- NAVIGATION BACK HANDLER LOGIC ---
    BackHandler(enabled = !showExitConfirmation && !passwordDialogOpen && !showImageDialog && !showImageSelectionDialog) {
        if (currentDestination != AppDestinations.HOME) {
            // If on Favorites or Profile, go back to Home
            currentDestination = AppDestinations.HOME
        } else {
            // If on Home, ask to exit
            showExitConfirmation = true
        }
    }

    LaunchedEffect(showSuccessToast) {
        if (showSuccessToast) {
            toastMessage = "Profile picture updated successfully"
            showCustomToast = true
            delay(2000)
            onHideSuccessToast()
        }
    }

    LaunchedEffect(userImageBitmap) {
        localImageBitmap = userImageBitmap
    }

    LaunchedEffect(passwordChangeSuccess) {
        if (passwordChangeSuccess) {
            currentDestination = AppDestinations.PROFILE
            onRefresh()
            passwordChangeSuccess = false

            toastMessage = "Password updated successfully"
            showCustomToast = true
            delay(2000)
            showCustomToast = false
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

        if (isUploadingImage) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
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
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Please wait",
                        color = Color.LightGray,
                        fontSize = 14.sp
                    )
                }
            }
        }

        if (isRefreshing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }

        Scaffold(
            topBar = {
                Column {
                    // Status bar spacer with your theme color
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) 24.dp else 32.dp)
                            .background(Color.Black.copy(alpha = 0.3f))
                    )

                    // Main toolbar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .background(Color.Black.copy(alpha = 0.3f))
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_logo_m),
                                contentDescription = "Logo",
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("MovieFlix", color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        if (currentDestination != AppDestinations.PROFILE) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = userName,
                                    fontSize = 14.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.width(100.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color.DarkGray)
                                        .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (localImageBitmap != null) {
                                        Image(
                                            bitmap = localImageBitmap!!.asImageBitmap(),
                                            contentDescription = "Profile",
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Text(
                                            text = if (userName.isNotEmpty()) userName.substring(0, 1).uppercase() else "U",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            bottomBar = {
                NavigationBar(
                    containerColor = Color.Transparent,
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.3f))
                ) {
                    AppDestinations.entries.forEach { item ->
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
            // Conditionally show SwipeRefresh or regular content
            if (!isUploadingImage) {
                SwipeRefresh(
                    state = rememberSwipeRefreshState(isRefreshing = isRefreshing),
                    onRefresh = onRefresh,
                    indicator = { state, trigger ->
                        SwipeRefreshIndicator(
                            state = state,
                            refreshTriggerDistance = trigger,
                            backgroundColor = Color.Transparent,
                            contentColor = Color.White
                        )
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        when (currentDestination) {
                            AppDestinations.HOME -> UserHomeContent(
                                userName = userName,
                                userImageBitmap = localImageBitmap,
                                movies = moviesList,
                                featuredMovie = featuredMovie,
                                isLoading = isLoadingMovies,
                                onMovieClicked = onMovieClicked,
                                modifier = Modifier.fillMaxSize()
                            )
                            AppDestinations.FAVORITES -> UserFavoritesContent(
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
                                onShowImageDialog = { showImageDialog = true },
                                onEditImageClicked = {
                                    showImageSelectionDialog = true
                                },
                                onPasswordChangeClicked = {
                                    passwordDialogOpen = true
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            } else {
                // When uploading, show content without swipe refresh
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (currentDestination) {
                        AppDestinations.HOME -> UserHomeContent(
                            userName = userName,
                            userImageBitmap = localImageBitmap,
                            movies = moviesList,
                            featuredMovie = featuredMovie,
                            isLoading = isLoadingMovies,
                            onMovieClicked = onMovieClicked,
                            modifier = Modifier.fillMaxSize()
                        )
                        AppDestinations.FAVORITES -> UserFavoritesContent(
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
                            onShowImageDialog = { showImageDialog = true },
                            onEditImageClicked = {
                                // Do nothing during upload
                            },
                            onPasswordChangeClicked = {
                                // Do nothing during upload
                            },
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
    }

    if (passwordDialogOpen) {
        ChangePasswordDialog(
            onDismiss = {
                passwordDialogOpen = false
            },
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
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Image(
                        bitmap = localImageBitmap!!.asImageBitmap(),
                        contentDescription = "Full screen profile picture",
                        modifier = Modifier
                            .size(300.dp)
                            .clip(CircleShape)
                            .border(2.dp, Color.White, CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Tap anywhere to close",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

// Add the missing composable functions that UserApp depends on

@Composable
fun UserHomeContent(
    userName: String,
    userImageBitmap: Bitmap?, // Keep parameter but not used here
    movies: List<Movie>,
    featuredMovie: Movie?,
    isLoading: Boolean,
    onMovieClicked: (Movie) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Featured Movie Section (Netflix-style hero banner)
        if (featuredMovie != null) {
            FeaturedMovieBanner(featuredMovie = featuredMovie, onClick = { onMovieClicked(featuredMovie) })
        }

        // Movies Grid Section
        Text(
            text = "Popular on MovieFlix",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp)
        )

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        } else if (movies.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Movie,
                        contentDescription = "No Movies",
                        tint = Color.LightGray,
                        modifier = Modifier.size(80.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No movies available",
                        fontSize = 16.sp,
                        color = Color.LightGray
                    )
                }
            }
        } else {
            // Netflix-style grid with 2 columns
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(movies) { movie ->
                    MovieGridItem(movie = movie, onClick = { onMovieClicked(movie) })
                }
            }
        }
    }
}

@Composable
fun FeaturedMovieBanner(featuredMovie: Movie, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Featured movie backdrop
            if (featuredMovie.posterUrl.isNotEmpty()) {
                AsyncImage(
                    model = featuredMovie.posterUrl,
                    contentDescription = "Featured: ${featuredMovie.title}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF450457), Color(0xFF120017))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Movie,
                        contentDescription = "No Image",
                        tint = Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.size(60.dp)
                    )
                }
            }

            // Gradient overlay for text readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.3f),
                                Color.Black.copy(alpha = 0.7f)
                            ),
                            startY = 0f,
                            endY = Float.POSITIVE_INFINITY
                        )
                    )
            )

            // Movie info overlay
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Premium badge
                if (featuredMovie.isPremium) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Yellow.copy(alpha = 0.9f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = "Premium",
                                tint = Color.Black,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "PREMIUM",
                                color = Color.Black,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Text(
                    text = featuredMovie.title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Movie metadata
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "${featuredMovie.releaseYear}",
                        fontSize = 14.sp,
                        color = Color.LightGray
                    )

                    // Duration
                    Text(
                        "${featuredMovie.duration} min",
                        fontSize = 14.sp,
                        color = Color.LightGray
                    )

                    // Genre indicator
                    if (featuredMovie.genreIds.isNotEmpty()) {
                        Text(
                            "Action",
                            fontSize = 14.sp,
                            color = Color.LightGray
                        )
                    }
                }

                // Play button
                Button(
                    onClick = onClick,
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .width(120.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    )
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Watch Now", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun MovieGridItem(movie: Movie, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(2/3f)
            .clickable { onClick() }
            .shadow(elevation = 4.dp, shape = RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.1f)
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // Movie Poster
            if (movie.posterUrl.isNotEmpty()) {
                SubcomposeAsyncImage(
                    model = movie.posterUrl,
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

            // Premium Badge
            if (movie.isPremium) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    PremiumBadge()
                }
            }

            // Title Overlay with Gradient
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.3f),
                                Color.Black.copy(alpha = 0.8f)
                            )
                        )
                    )
                    .padding(horizontal = 8.dp, vertical = 10.dp)
            ) {
                Column {
                    Text(
                        text = movie.title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Movie metadata
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "${movie.releaseYear}",
                            fontSize = 12.sp,
                            color = Color.LightGray
                        )

                        // Dot separator
                        Text(
                            "•",
                            fontSize = 12.sp,
                            color = Color.LightGray
                        )

                        Text(
                            "${movie.duration} min",
                            fontSize = 12.sp,
                            color = Color.LightGray
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PremiumBadge() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Yellow.copy(alpha = 0.9f))
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Star,
                contentDescription = "Premium",
                tint = Color.Black,
                modifier = Modifier.size(10.dp)
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                "PREMIUM",
                color = Color.Black,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun UserFavoritesContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Favorite,
                contentDescription = "Favorites",
                tint = Color.White,
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "My Favorites",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Your favorite movies will appear here",
                fontSize = 16.sp,
                color = Color.LightGray
            )
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
                InfoRow(icon = Icons.Default.Email, text = userEmail)
                HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
                InfoRow(icon = Icons.Default.Phone, text = userPhone)
                HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
                InfoRow(icon = Icons.Default.Cake, text = userDob)
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

@Preview(showBackground = true)
@Composable
fun UserPreview() {
    DemoTheme {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            UserHomeContent(
                userName = "John Doe",
                userImageBitmap = null,
                movies = listOf(
                    Movie(
                        id = "1",
                        title = "Sample Movie",
                        posterUrl = "https://image.tmdb.org/t/p/w500/sample.jpg",
                        releaseYear = 2024,
                        duration = 120,
                        isPremium = true
                    )
                ),
                featuredMovie = Movie(
                    id = "2",
                    title = "Featured Movie",
                    posterUrl = "https://image.tmdb.org/t/p/w500/featured.jpg",
                    releaseYear = 2024,
                    duration = 150,
                    isPremium = true
                ),
                isLoading = false,
                onMovieClicked = {}
            )
        }
    }
}