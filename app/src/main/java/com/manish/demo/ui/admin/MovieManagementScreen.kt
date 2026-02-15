package com.manish.demo.ui.admin
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
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

        Column(modifier = modifier
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
            Box(modifier = Modifier
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
    var streamUrl by remember { mutableStateOf("") }

    var showToast by remember { mutableStateOf(false) }
    val toastMessage = "Movie uploaded successfully"

    val searchResults by viewModel.searchResults.collectAsState()
    val isLoading by viewModel.isLoading
    val details by viewModel.selectedMovieDetails

    var selectedMovie by remember { mutableStateOf<TmdbMovieDto?>(null) }

    val primaryPurple = Color(0xFF2ECC71)
    val emeraldGreen = Color(0xFF2ECC71)
    val strongCardBg = Color.Transparent

    Box(modifier = Modifier.fillMaxSize()) {

        com.manish.demo.ui.components.CustomToastCompose(
            message = toastMessage,
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
                                contentDescription = "Clear search",
                                tint = Color.White
                            )
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF2ECC71),
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
                            .border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(16.dp)),
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
                                    modifier = Modifier.size(80.dp).clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                                    Text(movie.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    Text("Rating: ⭐ ${movie.rating}", color = Color.Yellow, fontSize = 11.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            Text("Auto-Source Status:", color = Color.White, fontSize = 11.sp)

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                                color = Color.Black.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Verified,
                                        contentDescription = null,
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

                            Spacer(modifier = Modifier.height(12.dp))

                            if (details != null) {
                                Text("Genres:", color = Color.White, fontSize = 11.sp)
                                LazyRow(modifier = Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(details?.genres ?: emptyList()) { genre ->
                                        Surface(
                                            color = emeraldGreen,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = genre.name,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            val buttonGradient = Brush.horizontalGradient(
                                colors = listOf(Color(0xFF00FF87), Color(0xFF60EFFF))
                            )
                            Button(
                                onClick = {
                                    val generatedUrl = "https://vidsrc.to/embed/movie/${movie.id}"
                                    val genreNames = details?.genres?.map { it.name } ?: emptyList()
                                    viewModel.uploadMovie(movie, generatedUrl, genreNames) {
                                        selectedMovie = null
                                        searchQuery = ""
                                        showToast = true
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                                    .height(52.dp)
                                    .shadow(
                                        elevation = 12.dp,
                                        shape = RoundedCornerShape(12.dp),
                                        ambientColor = Color(0xFF00FF87),
                                        spotColor = Color(0xFF00FF87),
                                    ),
                                enabled = details != null && !isLoading && !isAlreadyUploaded,
                                colors = ButtonDefaults.buttonColors(
                                    disabledContainerColor = Color.DarkGray
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = primaryPurple)
                                } else {
                                    Icon(Icons.Default.CloudDone, null, tint = Color.Black)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        if (isAlreadyUploaded) "Already Published" else "CONFIRM & PUBLISH",
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }

                            if (isAlreadyUploaded) {
                                Text(
                                    "This movie is already uploaded in your library",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = { selectedMovie = null },
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                    ) {
                        Icon(Icons.Default.Close, "Cancel", tint = Color.White.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

@Composable
fun ManageLibraryTabContent(viewModel: AdminViewModel) {
    val movies by viewModel.existingMovies.collectAsState()

    if (movies.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No movies in library", color = Color.Gray)
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(movies) { movie ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f))
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = movie["poster"].toString(),
                            contentDescription = null,
                            modifier = Modifier.size(50.dp).clip(RoundedCornerShape(4.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(movie["title"].toString(), color = Color.White, fontWeight = FontWeight.Medium, maxLines = 1)
                            Text(movie["genre"].toString(), color = Color.Gray, fontSize = 12.sp)
                        }
                        IconButton(onClick = { viewModel.deleteMovie(movie["docId"].toString()) }) {
                            Icon(Icons.Default.Delete, null, tint = Color.Red.copy(0.7f))
                        }
                    }
                }
            }
        }
    }
}
