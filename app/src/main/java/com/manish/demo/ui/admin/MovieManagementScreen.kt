package com.manish.demo.ui.admin


import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.manish.demo.R
import com.manish.demo.models.remote.TmdbMovieDto
import com.manish.demo.viewmodel.AdminViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoviesManagementScreen(
    viewModel: AdminViewModel,
    modifier: Modifier = Modifier
) {
    var selectedSubTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Add Movie", "Manage Library")

    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.padding(0.dp),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Your logo drawable
                        Image(
                            painter = painterResource(id = R.drawable.ic_logo_m),
                            contentDescription = "Logo",
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "MovieFlix",
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                windowInsets = WindowInsets(0)
            )
        },
        containerColor = Color.Transparent
    ) { innerPadding ->

        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // --- SUB-TABS ---
            TabRow(
                selectedTabIndex = selectedSubTab,
                containerColor = Color.Transparent,
                contentColor = Color.White,
                divider = {}
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedSubTab == index,
                        onClick = { selectedSubTab = index },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (selectedSubTab == index) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 14.sp
                            )
                        }
                    )
                }
            }

            // --- CONTENT ---
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                if (selectedSubTab == 0) {
                    AddMovieTabContent(viewModel)
                } else {
                    ManageLibraryTabContent(viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMovieTabContent(viewModel: AdminViewModel) {
    var searchQuery by remember { mutableStateOf("") }

    // --- NEW STATES FOR LOADER AND TOAST ---
    var isActionLoading by remember { mutableStateOf(false) }
    var showToast by remember { mutableStateOf(false) }
    var toastMsg by remember { mutableStateOf("") }

    val searchResults by viewModel.searchResults.collectAsState()
    val isLoading by viewModel.isLoading
    val details by viewModel.selectedMovieDetails

    var selectedMovie by remember { mutableStateOf<TmdbMovieDto?>(null) }

    val emeraldGreen = Color(0xFF2ECC71)
    val strongCardBg = Color.Transparent

    Box(modifier = Modifier.fillMaxSize()) {

        // --- 1. THE TOAST COMPONENT ---
        com.manish.demo.ui.components.CustomToastCompose(
            message = toastMsg,
            showToast = showToast,
            onDismiss = { showToast = false }
        )

        Column(modifier = Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    viewModel.searchMovies(it)
                },
                label = { Text("Search Movie Name", color = Color.LightGray) },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.White) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                searchQuery = ""
                                viewModel.searchMovies("")
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = Color.White
                            )
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = emeraldGreen,
                    unfocusedBorderColor = Color.Gray
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (selectedMovie == null) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(searchResults) { movie ->
                        Column(
                            modifier = Modifier
                                .width(110.dp)
                                .clickable {
                                    selectedMovie = movie
                                    searchQuery = movie.title
                                    viewModel.fetchFullMovieDetails(movie.id)
                                }
                        ) {
                            AsyncImage(
                                model = movie.fullPosterUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .height(160.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.DarkGray),
                                contentScale = ContentScale.Crop
                            )
                            Text(movie.title, color = Color.White, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
            }

            selectedMovie?.let { movie ->
                val isAlreadyUploaded = viewModel.isMovieInLibrary(movie.id)

                Box(modifier = Modifier.padding(top = 8.dp)) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                Color.White.copy(alpha = 0.6f),
                                RoundedCornerShape(16.dp)
                            ),
                        colors = CardDefaults.cardColors(containerColor = strongCardBg),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(end = 32.dp)
                            ) {
                                AsyncImage(
                                    model = movie.fullPosterUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Column(modifier = Modifier
                                    .padding(start = 12.dp)
                                    .weight(1f)) {
                                    Text(
                                        movie.title,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                    Text(
                                        "Rating: ⭐ ${movie.rating}",
                                        color = Color.Yellow,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))
                            Text("Auto-Source Status:", color = Color.White, fontSize = 11.sp)

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .border(
                                        1.dp,
                                        Color.White.copy(alpha = 0.3f),
                                        RoundedCornerShape(8.dp)
                                    ),
                                color = Color.Black.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Verified,
                                        null,
                                        tint = emeraldGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Aggregator Source Configured",
                                        color = Color.White,
                                        fontSize = 13.sp
                                    )
                                }
                            }

                            if (details != null) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Genres:", color = Color.White, fontSize = 11.sp)
                                LazyRow(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(details?.genres ?: emptyList()) { genre ->
                                        Surface(
                                            color = emeraldGreen,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                genre.name,
                                                color = Color.White,
                                                modifier = Modifier.padding(
                                                    horizontal = 10.dp,
                                                    vertical = 4.dp
                                                ),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // --- PUBLISH BUTTON WITH CALLBACK ---
                            Button(
                                onClick = {
                                    isActionLoading = true // Start the full-screen overlay loader
                                    val generatedUrl = "https://vidsrc.to/embed/movie/${movie.id}"
                                    val genreNames = details?.genres?.map { it.name } ?: emptyList()

                                    viewModel.uploadMovie(
                                        movie,
                                        generatedUrl,
                                        genreNames
                                    ) { success, message ->
                                        // This runs when Firebase/API finishes (Success OR Failure)
                                        isActionLoading = false
                                        toastMsg = message
                                        showToast = true

                                        if (success) {
                                            selectedMovie = null
                                            searchQuery = ""
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                                    .height(52.dp)
                                    .shadow(
                                        elevation = 12.dp,
                                        shape = RoundedCornerShape(12.dp),
                                        spotColor = emeraldGreen
                                    ),
                                enabled = details != null && !isActionLoading && !isAlreadyUploaded,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = emeraldGreen,
                                    disabledContainerColor = Color.DarkGray
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.CloudDone, null, tint = Color.Black)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (isAlreadyUploaded) "ALREADY PUBLISHED" else "CONFIRM & PUBLISH",
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.Black
                                )
                            }

                            if (isAlreadyUploaded) {
                                Text(
                                    "This movie is already in your library",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = { selectedMovie = null },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                    ) {
                        Icon(Icons.Default.Close, "Cancel", tint = Color.White.copy(alpha = 0.5f))
                    }
                }
            }
        }

        // --- 2. FULL SCREEN LOADER OVERLAY ---
        androidx.compose.animation.AnimatedVisibility(
            visible = isActionLoading,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .zIndex(5f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = emeraldGreen)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Uploading movie to database...",
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun ManageLibraryTabContent(viewModel: AdminViewModel) {
    val movies by viewModel.existingMovies.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var movieToDelete by remember { mutableStateOf<Map<String, Any>?>(null) }

    val filteredMovies = movies.filter { movie ->
        val title = movie["title"].toString().lowercase()
        val genre = movie["genres"].toString().lowercase()
        val query = searchQuery.lowercase()
        title.contains(query) || genre.contains(query)
    }

    Column(modifier = Modifier
        .fillMaxSize()
        .background(Color.Transparent)) {
        // --- SEARCH BAR (Keep your existing code here) ---
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            shape = RoundedCornerShape(12.dp),
            color = Color.White.copy(alpha = 0.08f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Icon(
                    Icons.Default.Search,
                    null,
                    tint = Color(0xFF2ECC71),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                    modifier = Modifier.weight(1f),
                    cursorBrush = SolidColor(Color(0xFF2ECC71)),
                    decorationBox = { inner ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (searchQuery.isEmpty()) Text(
                                "Search...",
                                color = Color.White.copy(0.5f)
                            )
                            inner()
                        }
                    }
                )
            }
        }

        // --- MOVIE LIST ---
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(16.dp)
        ) {
            items(items = filteredMovies, key = { it["docId"].toString() }) { movie ->
                TransparentMovieCard(
                    movie = movie,
                    onDeleteClick = {
                        movieToDelete = movie
                        showDeleteDialog = true
                    }
                )
            }
        }
    }

    // --- UPDATED DELETE DIALOG ---
    if (showDeleteDialog && movieToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Movie", color = Color.White) },
            text = {
                Text(
                    "Are you sure you want to delete \"${movieToDelete!!["title"]}\"?",
                    color = Color.White.copy(0.7f)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val docId = movieToDelete!!["docId"].toString()
                        val title = movieToDelete!!["title"].toString() // Get title for log

                        // Pass both ID and Title to match your new ViewModel signature
                        viewModel.deleteMovie(docId, title)

                        showDeleteDialog = false
                        movieToDelete = null
                    }
                ) { Text("Delete", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(
                        "Cancel",
                        color = Color.Gray
                    )
                }
            },
            containerColor = Color(0xFF1A1C1E)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class) // Only if using an older Compose version
@Composable
fun TransparentMovieCard(
    movie: Map<String, Any>,
    onDeleteClick: () -> Unit
) {
    val genresList = (movie["genres"] as? List<String>) ?: listOf()
    val rating = movie["rating"]?.toString() ?: "N/A"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(0.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Poster
            AsyncImage(
                model = movie["poster"].toString(),
                contentDescription = null,
                modifier = Modifier
                    .size(80.dp) // Slightly larger for better look
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Movie details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = movie["title"].toString(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "⭐ $rating",
                    color = Color.Yellow,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                // --- FIXED GENRE SECTION ---
                // FlowRow handles the wrapping automatically
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    genresList.forEach { genre ->
                        Surface(
                            color = Color(0xFF2ECC71).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, Color(0xFF2ECC71).copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = genre,
                                color = Color(0xFF2ECC71),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Delete button
            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = Color.Red.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}