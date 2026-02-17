package com.manish.demo

import androidx.compose.ui.text.style.TextOverflow
import com.google.firebase.messaging.FirebaseMessaging
import com.google.android.gms.tasks.Task
import android.os.Build
import android.os.Build.VERSION_CODES
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.zIndex
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.manish.demo.ui.ChangePasswordDialog
import com.manish.demo.ui.InfoRow
import com.manish.demo.ui.admin.MoviesManagementScreen
import com.manish.demo.ui.admin.SubscriptionManagementScreen
import com.manish.demo.ui.admin.UsersManagementScreen
import com.manish.demo.ui.components.CustomToastCompose
import com.manish.demo.ui.components.ImageSelectionDialog
import com.manish.demo.ui.theme.DemoTheme
import com.manish.demo.viewmodel.AdminViewModel
import com.manish.demo.viewmodel.SubscriptionViewModel
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.compareTo
import kotlin.div
import kotlin.or
import kotlin.text.compareTo
import kotlin.text.toInt
import kotlin.times

// --- DATA MODELS & CONSTANTS ---
data class DashboardStats(
    val totalUsers: Int = 0,
    val totalMovies: Int = 0,
    val activeSubs: Int = 0,
    val totalRevenue: Long = 0L,
    val weeklyRevenue: List<Float> = listOf(0.1f, 0.1f, 0.1f, 0.1f, 0.1f, 0.1f, 0.1f),
    val recentActivities: List<DashboardActivityItem> = emptyList()
)
data class DashboardActivityItem(val title: String, val time: String, val icon: ImageVector, val color: Color)

private val dashEmerald = Color(0xFF2ECC71)
private val dashGlassBg = Color.White.copy(alpha = 0.05f)
private val dashGlassBorder = Color.White.copy(alpha = 0.1f)

// --- DASHBOARD VIEWMODEL ---
class DashboardViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val _stats = mutableStateOf(DashboardStats())
    val stats: State<DashboardStats> = _stats

    private val dashEmerald = Color(0xFF2ECC71)

    init {
        startListening()
    }

    private fun startListening() {
        // 1. LIVE SUMMARY STATS (The Cards)
        // Matches your screenshot: Collection "Dashboard_stats"
        db.collection("Dashboard_stats")
            .limit(1) // Automatically finds your one document even with a random ID
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("DASH_DEBUG", "Stats Error: ${error.message}")
                    return@addSnapshotListener
                }

                val doc = snapshot?.documents?.firstOrNull()
                if (doc != null && doc.exists()) {
                    // Safe number parsing
                    val users = (doc.get("totalUsers") as? Number)?.toInt() ?: 0
                    val movies = (doc.get("totalMovies") as? Number)?.toInt() ?: 0
                    val subs = (doc.get("activeSubs") as? Number)?.toInt() ?: 0
                    val rev = (doc.get("totalRevenue") as? Number)?.toLong() ?: 0L

                    _stats.value = _stats.value.copy(
                        totalUsers = users,
                        totalMovies = movies,
                        activeSubs = subs,
                        totalRevenue = rev
                    )
                    Log.d("DASH_DEBUG", "Stats updated: $users users, $rev revenue")
                }
            }

        // 2. DYNAMIC WEEKLY REVENUE (The Graph)
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -7)
        val oneWeekAgo = cal.time

        db.collection("subscriptions")
            .whereGreaterThan("timestamp", Timestamp(oneWeekAgo)) // Using your new field name
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("DASH_DEBUG", "Chart Error: ${error.message}")
                    return@addSnapshotListener
                }

                val dayTotals = mutableMapOf<Int, Float>()

                snapshot?.documents?.forEach { d ->
                    val ts = d.getTimestamp("timestamp") // Using your new field name
                    ts?.let {
                        val c = Calendar.getInstance().apply { time = it.toDate() }
                        val dayKey = c.get(Calendar.DAY_OF_YEAR)

                        // UPDATED: Using 'pricePaid' as per your requirement
                        val price = (d.get("pricePaid") as? Number)?.toFloat() ?: 0f

                        dayTotals[dayKey] = (dayTotals[dayKey] ?: 0f) + price
                    }
                }

                val maxRevenue = dayTotals.values.maxOrNull() ?: 1f
                val chartData = mutableListOf<Float>()

                // Build 7 bars representing the last 7 days
                for (i in 6 downTo 0) {
                    val tCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -i) }
                    val dayOfYear = tCal.get(Calendar.DAY_OF_YEAR)
                    val dailySum = dayTotals[dayOfYear] ?: 0f

                    // Normalizing bar height (0.1 to 1.0)
                    val barHeight = if (maxRevenue > 0) (dailySum / maxRevenue).coerceAtLeast(0.1f) else 0.1f
                    chartData.add(barHeight)
                }

                _stats.value = _stats.value.copy(weeklyRevenue = chartData)
                Log.d("DASH_DEBUG", "Chart updated with ${chartData.size} bars")
            }

        // 3. LIVE ACTIVITY FEED
        db.collection("activities")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(10)
            .addSnapshotListener { snapshot, _ ->
                val list = snapshot?.documents?.mapNotNull { doc ->
                    val type = doc.getString("type") ?: ""
                    DashboardActivityItem(
                        title = doc.getString("title") ?: "Activity",
                        time = formatTime(doc.getTimestamp("timestamp")),
                        icon = when (type) {
                            "ban" -> Icons.Default.Block
                            "movie" -> Icons.Default.Movie
                            "sub" -> Icons.Default.AddCircle
                            "role" -> Icons.Default.AdminPanelSettings
                            "plan" -> Icons.Default.SettingsSuggest
                            "security" -> Icons.Default.LockReset
                            else -> Icons.Default.Notifications
                        },
                        color = when (type) {
                            "ban" -> Color.Red
                            "movie" -> Color.Cyan
                            "sub" -> dashEmerald
                            "security", "plan" -> Color.Yellow
                            else -> Color.LightGray
                        }
                    )
                } ?: emptyList()
                _stats.value = _stats.value.copy(recentActivities = list)
            }
    }
    private fun formatTime(ts: Timestamp?): String {
        if (ts == null) return "Now"
        val seconds = (System.currentTimeMillis() / 1000) - ts.seconds
        return when {
            seconds < 60 -> "Just now"
            seconds < 3600 -> "${seconds / 60}m ago"
            seconds < 86400 -> "${seconds / 3600}h ago"
            else -> SimpleDateFormat("dd MMM", Locale.getDefault()).format(ts.toDate())
        }
    }
}

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
    private var hasActiveSubscription by mutableStateOf(false)
    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) handleImageSelection(uri)
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) handleCameraImage(bitmap)
    }
    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            handleImageSourceSelection("camera_action")
        } else {
            if (!ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.CAMERA)) {
                showSettingsDialog("Camera")
            }
        }
    }
    private val requestGalleryPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            handleImageSourceSelection("gallery_action")
        } else {
            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                android.Manifest.permission.READ_MEDIA_IMAGES
            else
                android.Manifest.permission.READ_EXTERNAL_STORAGE

            if (!ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                showSettingsDialog("Gallery/Storage")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermission()
        subscribeToAdminTopic()
        listenForRoleSecurity()
        fetchAdminData()
    }
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 102)
            }
        }
    }

    private fun subscribeToAdminTopic() {
        // This ensures this specific device receives alerts sent to "admins"
        com.google.firebase.messaging.FirebaseMessaging.getInstance().subscribeToTopic("admins")
            .addOnCompleteListener { task ->
                if (task.isSuccessful) Log.d("FCM", "Subscribed to admin alerts")
            }
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
                        passwordLastUpdated = if (lastUpdated != null) SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()).format(lastUpdated) else "Never"
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
        } else { isRefreshing = false; updateUI() }
    }

    private fun updateUI() {
        setContent {
            DemoTheme {
                AdminApp(
                    adminName = adminName, adminEmail = adminEmail, adminPhone = adminPhone, adminDob = adminDob,
                    adminImageBitmap = adminImageBitmap, passwordLastUpdated = passwordLastUpdated, isRefreshing = isRefreshing,
                    showSuccessToast = showSuccessToast, isUploadingImage = isUploadingImage,
                    onRefresh = { if (!isUploadingImage) { isRefreshing = true; fetchAdminData() } },
                    onHideSuccessToast = { showSuccessToast = false },
                    onImageSelected = { handleImageSourceSelection(it) }
                )
            }
        }
    }

    private fun handleImageSourceSelection(sourceType: String) {
        when (sourceType) {
            "gallery" -> {
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
                cameraLauncher.launch(null)
            }
        }
    }

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


    private fun handleImageSelection(imageUri: Uri) {
        isUploadingImage = true
        updateUI()

        try {
            Log.d("AdminHome", "Processing image from URI: $imageUri")

            // First, get image dimensions without loading the full bitmap
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

            var inputStream: InputStream? = contentResolver.openInputStream(imageUri)
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()

            val imageWidth = options.outWidth
            val imageHeight = options.outHeight
            Log.d("AdminHome", "Original image size: ${imageWidth}x${imageHeight}")

            if (imageWidth <= 0 || imageHeight <= 0) {
                Log.e("AdminHome", "Invalid image dimensions")
                isUploadingImage = false
                updateUI()
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

            Log.d("AdminHome", "Using sample size: $sampleSize")

            // Now decode with sample size to reduce memory
            val decodingOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565 // Use less memory
            }

            inputStream = contentResolver.openInputStream(imageUri)
            val bitmap = BitmapFactory.decodeStream(inputStream, null, decodingOptions)
            inputStream?.close()

            if (bitmap != null) {
                Log.d("AdminHome", "Bitmap decoded successfully: ${bitmap.width}x${bitmap.height}")

                // Further scale if still too large
                val scaledBitmap = if (bitmap.width > 800 || bitmap.height > 800) {
                    val scale = 800f / Math.max(bitmap.width, bitmap.height)
                    val newWidth = (bitmap.width * scale).toInt()
                    val newHeight = (bitmap.height * scale).toInt()
                    Log.d("AdminHome", "Scaling to: ${newWidth}x${newHeight}")
                    val scaled = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
                    bitmap.recycle() // Free original bitmap memory
                    scaled
                } else {
                    bitmap
                }

                updateProfileImageInFirebase(convertBitmapToBase64(scaledBitmap))
                scaledBitmap.recycle() // Free scaled bitmap memory

            } else {
                Log.e("AdminHome", "Failed to decode bitmap from gallery URI")
                isUploadingImage = false
                updateUI()
            }

        } catch (e: OutOfMemoryError) {
            Log.e("AdminHome", "Out of memory while processing image", e)
            isUploadingImage = false
            updateUI()
        } catch (e: Exception) {
            Log.e("AdminHome", "Error processing gallery image", e)
            isUploadingImage = false
            updateUI()
        }
    }



    private fun handleCameraImage(bitmap: Bitmap) {
        isUploadingImage = true; updateUI()
        updateProfileImageInFirebase(convertBitmapToBase64(bitmap))
    }

    private fun convertBitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        // Use higher quality compression, the bitmap is already scaled down
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        val byteArray = outputStream.toByteArray()
        Log.d("AdminHome", "Base64 size: ${byteArray.size / 1024}KB")
        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }


    private fun updateProfileImageInFirebase(base64Image: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        userId?.let {
            FirebaseFirestore.getInstance().collection("users").document(it)
                .update("profileImage", base64Image)
                .addOnSuccessListener { showSuccessToast = true; isUploadingImage = false; fetchAdminData() }
                .addOnFailureListener { isUploadingImage = false; updateUI() }
        }
    }

    private fun listenForRoleSecurity() {
        val user = FirebaseAuth.getInstance().currentUser
        user?.let {
            FirebaseFirestore.getInstance().collection("users").document(it.uid)
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null && snapshot.exists()) {
                        val role = snapshot.getString("role") ?: "USER"
                        if (!role.equals("admin", ignoreCase = true)) showSecurityDialog()
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
                    startActivity(Intent(this, LoginActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK })
                    finish()
                }.show()
        }
    }

    private fun checkPermission(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED
    private fun requestPermission(p: String, c: Int) = ActivityCompat.requestPermissions(this, arrayOf(p), c)
}

enum class AdminDestinations(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    MOVIES("Movies", Icons.Default.Movie),
    USERS("Users", Icons.Default.Person),
    SUBSCRIPTIONS("Subs", Icons.Default.Subscriptions),
    PROFILE("Profile", Icons.Default.AccountCircle),
}

// ... existing imports ...

// ... existing code ...

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminApp(
    adminName: String, adminEmail: String, adminPhone: String, adminDob: String,
    adminImageBitmap: Bitmap?, passwordLastUpdated: String, isRefreshing: Boolean,
    showSuccessToast: Boolean, isUploadingImage: Boolean, onRefresh: () -> Unit,
    onHideSuccessToast: () -> Unit, onImageSelected: (String) -> Unit
) {
    var currentDestination by rememberSaveable { mutableStateOf(AdminDestinations.HOME) }

    // ✅ ADD IMAGE DIALOG STATE
    var showImageDialog by remember { mutableStateOf(false) }

    var showImageSelectionDialog by remember { mutableStateOf(false) }
    var showExitConfirmation by remember { mutableStateOf(false) }
    var passwordDialogOpen by remember { mutableStateOf(false) }
    var showCustomToast by remember { mutableStateOf(false) }
    var toastMessage by remember { mutableStateOf("") }

    val adminViewModel: AdminViewModel = viewModel()
    val subscriptionViewModel: SubscriptionViewModel = viewModel()
    val dashViewModel: DashboardViewModel = viewModel()
    val context = LocalContext.current
    val activity = context as? Activity
    val dashStats by dashViewModel.stats

    BackHandler(enabled = !showExitConfirmation && !passwordDialogOpen && !showImageDialog) {
        when {
            showImageDialog -> showImageDialog = false
            currentDestination != AdminDestinations.HOME -> currentDestination = AdminDestinations.HOME
            else -> showExitConfirmation = true
        }
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
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.7f)).zIndex(10f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Text("Uploading image...", color = Color.White, modifier = Modifier.padding(top = 16.dp))
                }
            }
        }

        Scaffold(
            topBar = {
                if (currentDestination != AdminDestinations.MOVIES) {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(painter = painterResource(id = R.drawable.ic_logo_m), contentDescription = "Logo", modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("MovieFlix", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        },
                        actions = {
                            if (currentDestination == AdminDestinations.HOME) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.End,
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Text(
                                        text = adminName.split(" ").firstOrNull() ?: adminName,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )

                                    if (adminImageBitmap != null) {
                                        Image(
                                            bitmap = adminImageBitmap.asImageBitmap(),
                                            contentDescription = "Admin Profile",
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .border(1.5.dp, Color(0xFF2ECC71), CircleShape),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.AccountCircle,
                                            contentDescription = "Admin Profile",
                                            tint = Color.White,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                }
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
                            icon = {
                                if (item == AdminDestinations.PROFILE && adminImageBitmap != null) {
                                    Image(
                                        bitmap = adminImageBitmap.asImageBitmap(),
                                        contentDescription = item.label,
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .border(1.dp, if (item == currentDestination) Color.White else Color.Gray, CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(item.icon, null)
                                }
                            },
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
            SwipeRefresh(
                state = rememberSwipeRefreshState(isRefreshing = isRefreshing),
                onRefresh = onRefresh,
                swipeEnabled = currentDestination != AdminDestinations.MOVIES && !isUploadingImage,
                modifier = Modifier.fillMaxSize().padding(innerPadding)
            ) {
                when (currentDestination) {
                    AdminDestinations.HOME -> AdminDashboardContent(
                        adminName = adminName,
                        stats = dashStats,
                        onNavigate = { currentDestination = it }
                    )
                    AdminDestinations.MOVIES -> MoviesManagementScreen(viewModel = adminViewModel, modifier = Modifier.fillMaxSize())
                    AdminDestinations.USERS -> UsersManagementScreen(viewModel = adminViewModel, modifier = Modifier.fillMaxSize())
                    AdminDestinations.SUBSCRIPTIONS -> SubscriptionManagementScreen(viewModel = subscriptionViewModel, modifier = Modifier.fillMaxSize())
                    AdminDestinations.PROFILE -> AdminProfileContent(
                        adminName, adminEmail, adminPhone, adminDob, adminImageBitmap, passwordLastUpdated, isUploadingImage,
                        onShowImageDialog = { showImageDialog = true }, // ✅ ADDED
                        onEditImageClicked = { showImageSelectionDialog = true },
                        onPasswordChangeClicked = { passwordDialogOpen = true }
                    )
                }
            }
        }

        // ✅ ADD IMAGE ENLARGEMENT DIALOG
        if (showImageDialog && adminImageBitmap != null) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showImageDialog = false },
                properties = androidx.compose.ui.window.DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.85f))
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null,
                            onClick = { showImageDialog = false }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = adminImageBitmap.asImageBitmap(),
                        contentDescription = "Full Size Profile",
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .border(3.dp, Color.White, CircleShape)
                            .clickable(enabled = false) {},
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
                onPasswordChanged = { _ ->
                    passwordDialogOpen = false
                    onRefresh()
                }
            )
        }
    }
}



// --- AMAZING DYNAMIC DASHBOARD ---
@Composable
fun AdminDashboardContent(adminName: String, stats: DashboardStats, onNavigate: (AdminDestinations) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                Text("Welcome back,", color = Color.White.copy(0.6f), fontSize = 14.sp)
                Text(adminName, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DashboardStatCard(Modifier.weight(1f), "Total Users", "${stats.totalUsers}", Icons.Default.Groups, dashEmerald)
                    DashboardStatCard(Modifier.weight(1f), "Revenue", "₹${if(stats.totalRevenue >= 1000) "${stats.totalRevenue/1000}K" else stats.totalRevenue}", Icons.Default.AttachMoney, Color.Yellow)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DashboardStatCard(Modifier.weight(1f), "Active Subs", "${stats.activeSubs}", Icons.Default.Subscriptions, Color.Cyan)
                    DashboardStatCard(Modifier.weight(1f), "Movies", "${stats.totalMovies}", Icons.Default.Movie, Color.Magenta)
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = dashGlassBg),
                border = BorderStroke(1.dp, dashGlassBorder),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Weekly Revenue Trend", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(20.dp))
                    Row(modifier = Modifier.fillMaxWidth().height(100.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                        stats.weeklyRevenue.forEach { h ->
                            val animatedHeight by animateFloatAsState(targetValue = h)
                            Box(modifier = Modifier.weight(1f).fillMaxHeight(animatedHeight).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).background(Brush.verticalGradient(listOf(dashEmerald, dashEmerald.copy(0.1f)))))
                        }
                    }
                }
            }
        }

        item {
            Text("Management Hub", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DashboardActionIcon("Users", Icons.Default.People, dashEmerald) { onNavigate(AdminDestinations.USERS) }
                DashboardActionIcon("Movies", Icons.Default.VideoLibrary, Color.Cyan) { onNavigate(AdminDestinations.MOVIES) }
                DashboardActionIcon("Subs", Icons.Default.CardMembership, Color.Yellow) { onNavigate(AdminDestinations.SUBSCRIPTIONS) }
                DashboardActionIcon("Profile", Icons.Default.AccountCircle, Color.White) { onNavigate(AdminDestinations.PROFILE) }
            }
        }

        item { Text("Recent Activities", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp) }

        items(stats.recentActivities) { activity ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(36.dp).background(activity.color.copy(0.1f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(activity.icon, null, Modifier.size(16.dp), activity.color)
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(activity.title, color = Color.White, fontSize = 14.sp)
                    Text(activity.time, color = Color.White.copy(0.4f), fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun DashboardStatCard(modifier: Modifier, title: String, value: String, icon: ImageVector, color: Color) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = dashGlassBg), border = BorderStroke(1.dp, dashGlassBorder), shape = RoundedCornerShape(20.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Box(modifier = Modifier.size(32.dp).background(color.copy(0.1f), CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(18.dp), color) }
            Spacer(Modifier.height(12.dp))
            Text(value, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(title, color = Color.White.copy(0.5f), fontSize = 11.sp)
        }
    }
}

@Composable
fun DashboardActionIcon(label: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }) {
        Box(modifier = Modifier.size(60.dp).background(dashGlassBg, RoundedCornerShape(16.dp)).border(1.dp, dashGlassBorder, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = color) }
        Spacer(Modifier.height(8.dp))
        Text(label, color = Color.White.copy(0.8f), fontSize = 11.sp)
    }
}

// --- PROFILE SECTION ---
@Composable
fun AdminProfileContent(
    adminName: String, adminEmail: String, adminPhone: String, adminDob: String,
    adminImageBitmap: Bitmap?, passwordLastUpdated: String, isUploadingImage: Boolean,
    onShowImageDialog: () -> Unit, // ✅ ADDED PARAMETER
    onEditImageClicked: () -> Unit, onPasswordChangeClicked: () -> Unit
) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {

        // ✅ UPDATED PROFILE IMAGE SECTION WITH EDIT ICON
        Box(contentAlignment = Alignment.Center) {
            Box(contentAlignment = Alignment.BottomEnd) {
                if (adminImageBitmap != null) {
                    Image(
                        bitmap = adminImageBitmap.asImageBitmap(),
                        contentDescription = "Admin Profile",
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
                        Icons.Default.AccountCircle,
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

                // ✅ EDIT ICON OVERLAY
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

            // ✅ LOADING INDICATOR
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

        Spacer(Modifier.height(16.dp))
        Text(adminName, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AdminPanelSettings, null, tint = Color.Cyan, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Administrator", fontSize = 14.sp, color = Color.LightGray)
        }
        Spacer(Modifier.height(32.dp))
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White.copy(0.1f))) {
            Column(modifier = Modifier.padding(16.dp)) {
                InfoRow(icon = Icons.Default.Email, label = "Email", text = adminEmail)
                HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
                InfoRow(icon = Icons.Default.Phone, label = "Phone", text = adminPhone)
                HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
                InfoRow(icon = Icons.Default.Cake, label = "Date of Birth", text = adminDob)
                HorizontalDivider(color = Color.White.copy(0.2f))
                Row(modifier = Modifier.fillMaxWidth().clickable { if (!isUploadingImage) onPasswordChangeClicked() }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, null, tint = if (isUploadingImage) Color.Gray else Color.White, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Password", color = if (isUploadingImage) Color.Gray else Color.White)
                        Text("Last updated: $passwordLastUpdated", fontSize = 12.sp, color = if (isUploadingImage) Color.DarkGray else Color.LightGray)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = if (isUploadingImage) Color.Gray else Color.White)
                }
            }
        }
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = {
                if (!isUploadingImage) {
                    FirebaseAuth.getInstance().signOut()
                    context.startActivity(Intent(context, LoginActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK })
                    (context as Activity).finish()
                }
            },
            enabled = !isUploadingImage,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Red.copy(alpha = if (isUploadingImage) 0.3f else 0.7f)
            )
        ) {
            Icon(Icons.Default.Logout, null, tint = if (isUploadingImage) Color.LightGray else Color.White)
            Spacer(Modifier.width(8.dp))
            Text("Logout", color = if (isUploadingImage) Color.LightGray else Color.White, fontWeight = FontWeight.Bold)
        }
    }
}
