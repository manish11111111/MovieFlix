package com.manish.demo.screens.admin

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import com.manish.demo.models.Genre
import com.manish.demo.models.Movie
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

// --- HELPER EXTENSION FOR SAFE COLORS ---
fun String.toColor(): Color {
    return try {
        Color(android.graphics.Color.parseColor(this))
    } catch (e: Exception) {
        Color(0xFF6200EE) // Default fallback color
    }
}

@OptIn(ExperimentalAnimationApi::class)
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun MoviesManagementScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // State variables
    var movies by remember { mutableStateOf<List<Movie>>(emptyList()) }
    var genres by remember { mutableStateOf<List<Genre>>(emptyList()) }
    var showAddMovieDialog by remember { mutableStateOf(false) }
    var showAddGenreDialog by remember { mutableStateOf(false) }
    var selectedMovieForEdit by remember { mutableStateOf<Movie?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Movies, 1 = Genres
    var isLoadingData by remember { mutableStateOf(true) }

    // Initial Data Load
    LaunchedEffect(Unit) {
        isLoadingData = true
        refreshData(
            onSuccess = { m, g ->
                movies = m
                genres = g
                isLoadingData = false
            },
            onError = { isLoadingData = false }
        )
    }

    fun handleRefresh() {
        scope.launch {
            refreshData(
                onSuccess = { m, g ->
                    movies = m
                    genres = g
                },
                onError = { }
            )
        }
    }

    // Cosmic gradient background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0A0A1A),
                        Color(0xFF121234),
                        Color(0xFF1A1A3A),
                        Color(0xFF222244)
                    )
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Header with Real Premium Count
            CosmicNebulaHeader(
                moviesCount = movies.size,
                genresCount = genres.size,
                premiumCount = movies.count { it.isPremium } // FIXED: Calculates actual premium movies
            )

            // Tabs
            GlassMorphicTabBar(selectedTab = selectedTab, onTabSelected = { selectedTab = it })

            Spacer(modifier = Modifier.height(16.dp))

            // Content Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(300)) togetherWith
                                fadeOut(animationSpec = tween(300))
                    },
                    label = "ContentTransition",
                    modifier = Modifier.fillMaxSize()
                ) { tab ->
                    when (tab) {
                        0 -> {
                            if (movies.isEmpty() && !isLoadingData) {
                                // FIXED: Removed Action Button (FAB is enough)
                                CosmicEmptyState(
                                    icon = Icons.Default.MovieCreation,
                                    title = "Galaxy Awaits Your Stories",
                                    description = "Your cinematic universe begins here. Add your first masterpiece."
                                )
                            } else {
                                StunningMoviesGrid(
                                    movies = movies,
                                    genres = genres,
                                    onMovieEdit = { movie ->
                                        selectedMovieForEdit = movie
                                        showAddMovieDialog = true
                                    },
                                    onMovieDelete = { movieId ->
                                        scope.launch {
                                            deleteMovie(movieId)
                                            handleRefresh()
                                            Toast.makeText(
                                                context,
                                                "Movie deleted",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                )
                            }
                        }
                        // ... inside MoviesManagementScreen -> AnimatedContent -> when(tab) -> 1 ...

                        1 -> {
                            if (genres.isEmpty() && !isLoadingData) {
                                CosmicEmptyState(
                                    icon = Icons.Default.Category,
                                    title = "Craft Your Categories",
                                    description = "Organize your cinematic universe with custom genres."
                                )
                            } else {
                                StunningGenresGrid(
                                    genres = genres,
                                    movies = movies, // ✅ Pass the movies list here
                                    onDeleteGenre = { genreId ->
                                        scope.launch {
                                            deleteGenre(genreId)
                                            handleRefresh()
                                            Toast.makeText(
                                                context,
                                                "Genre deleted",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Floating Action Button
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 32.dp, end = 32.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            FloatingActionButton(
                onClick = {
                    if (selectedTab == 0) showAddMovieDialog = true else showAddGenreDialog = true
                },
                containerColor = Color(0xFF00D4FF),
                modifier = Modifier.size(64.dp)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Add",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }

    // Dialogs
    if (showAddMovieDialog) {
        PremiumAddMovieDialog(
            movie = selectedMovieForEdit,
            genres = genres,
            onDismiss = {
                showAddMovieDialog = false
                selectedMovieForEdit = null
            },
            onSave = {
                handleRefresh()
            }
        )
    }

    if (showAddGenreDialog) {
        PremiumAddGenreDialog(
            onDismiss = { showAddGenreDialog = false },
            onSave = { handleRefresh() }
        )
    }
}

// ==============================
// UPDATED HEADER (With Premium Count)
// ==============================

@Composable
fun CosmicNebulaHeader(
    moviesCount: Int,
    genresCount: Int,
    premiumCount: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .height(200.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x15FFFFFF)),
        shape = RoundedCornerShape(30.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(1.dp, Color(0x30FFFFFF), RoundedCornerShape(30.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "🎬 MOVIEFLIX STUDIO",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 26.sp
                    )
                    Text(
                        "Manage your streaming content",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatCard("MOVIES", moviesCount.toString(), Icons.Default.Movie)
                    StatCard("GENRES", genresCount.toString(), Icons.Default.Category)
                    // FIXED: Now displays actual premium count
                    StatCard("PREMIUM", premiumCount.toString(), Icons.Default.Star)
                }
            }
        }
    }
}

// ==============================
// REFACTORED HEADER
// ==============================

@Composable
fun CosmicEmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .background(Color(0x10FFFFFF), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = Color.White.copy(0.5f),
                modifier = Modifier.size(40.dp)
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            title,
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            description,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
        // FIXED: Button removed as requested
    }
}

@Composable
fun StatCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = Color(0xFF00D4FF), modifier = Modifier.size(24.dp))
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Text(title, color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
    }
}

// ==============================
// MOVIES GRID (Fixed Image Handling)
// ==============================

@Composable
fun StunningMoviesGrid(
    movies: List<Movie>,
    genres: List<Genre>,
    onMovieEdit: (Movie) -> Unit,
    onMovieDelete: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // FIXED: defined all sides explicitly
        contentPadding = PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        items(movies) { movie ->
            StunningMovieCard(movie, genres, { onMovieEdit(movie) }, { onMovieDelete(movie.id) })
        }
    }
}

@Composable
fun StunningMovieCard(
    movie: Movie,
    genres: List<Genre>,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    // Safe lookup of genres
    val movieGenres = genres.filter { movie.genreIds.contains(it.id) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x15FFFFFF)),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.DarkGray)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(movie.posterUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (movie.isPremium) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .background(Color(0xFFFFD700), RoundedCornerShape(bottomEnd = 12.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "PRO",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        movie.title,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Box {
                        IconButton(onClick = { showMenu = true }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.MoreVert, null, tint = Color.White)
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(Color(0xFF1A1A2E))
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit", color = Color.White) },
                                onClick = { showMenu = false; onEdit() },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Edit,
                                        null,
                                        tint = Color(0xFF00D4FF)
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = Color.Red) },
                                onClick = { showMenu = false; onDelete() },
                                leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color.Red) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(movieGenres) { genre ->
                        Text(
                            text = genre.name,
                            color = genre.color.toColor(),
                            fontSize = 10.sp,
                            modifier = Modifier
                                .border(1.dp, genre.color.toColor(), RoundedCornerShape(50))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    "${movie.releaseYear} • ${movie.duration} min",
                    color = Color.White.copy(0.6f),
                    fontSize = 12.sp
                )
            }
        }
    }
}

// ==============================
// GENRES GRID
// ==============================

@Composable
fun StunningGenresGrid(
    genres: List<Genre>,
    movies: List<Movie>, // ✅ Added this parameter
    onDeleteGenre: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(genres.chunked(2)) { rowGenres ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                rowGenres.forEach { genre ->
                    // ✅ Dynamic Calculation: Count movies that have this genre ID
                    val realCount = movies.count { it.genreIds.contains(genre.id) }

                    GenreCard(
                        genre = genre,
                        movieCount = realCount, // Pass calculated count
                        modifier = Modifier.weight(1f),
                        onDelete = onDeleteGenre
                    )
                }
                if (rowGenres.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun GenreCard(
    genre: Genre,
    movieCount: Int, // ✅ Added this parameter
    modifier: Modifier,
    onDelete: (String) -> Unit
) {
    var showDelete by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.height(120.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0x15FFFFFF)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(genre.color.toColor().copy(alpha = 0.15f))
                .border(1.dp, genre.color.toColor().copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                .clickable { showDelete = true }
                .padding(16.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(Icons.Default.MovieFilter, null, tint = genre.color.toColor())
                Column {
                    Text(
                        genre.name,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    // ✅ Use the passed 'movieCount' here
                    Text("$movieCount titles", color = Color.White.copy(0.6f), fontSize = 12.sp)
                }
            }
        }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            containerColor = Color(0xFF1A1A2E),
            title = { Text("Delete Genre?", color = Color.White) },
            text = { Text("Are you sure you want to remove ${genre.name}?", color = Color.Gray) },
            confirmButton = {
                TextButton(onClick = { onDelete(genre.id); showDelete = false }) {
                    Text("DELETE", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("CANCEL", color = Color.White) }
            }
        )
    }
}
// ==============================
// PREMIUM ADD MOVIE DIALOG (Fixed Keyboard Handling)
// ==============================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PremiumAddMovieDialog(
    movie: Movie?,
    genres: List<Genre>,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    var title by remember { mutableStateOf(movie?.title ?: "") }
    var description by remember { mutableStateOf(movie?.description ?: "") }
    var trailerUrl by remember { mutableStateOf(movie?.trailerUrl ?: "") }
    var posterUrl by remember { mutableStateOf(movie?.posterUrl ?: "") }
    var duration by remember { mutableStateOf(movie?.duration?.toString() ?: "120") }
    var releaseYear by remember { mutableStateOf(movie?.releaseYear?.toString() ?: "2024") }
    var isPremium by remember { mutableStateOf(movie?.isPremium ?: false) }

    val initialSelectedGenres =
        remember { movie?.genreIds?.toMutableStateList() ?: mutableStateListOf() }

    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // ✅ FIX: Track if any field has focus
    val focusManager = LocalFocusManager.current
    var hasTextFieldFocus by remember { mutableStateOf(false) }

    // ✅ FIX: Handle back press - clear focus first, then dismiss
    BackHandler(enabled = !isLoading) {
        if (hasTextFieldFocus) {
            // Clear focus and hide keyboard
            focusManager.clearFocus()
            hasTextFieldFocus = false
        } else {
            // No field has focus, close the dialog
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false // We handle it manually
        ),
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .fillMaxHeight(0.9f),
        containerColor = Color(0xFF121225),
        title = null,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    if (movie == null) "✨ NEW MOVIE" else "✏️ EDIT MOVIE",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )

                CustomTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = "Title",
                    onFocusChange = { hasTextFieldFocus = it }
                )
                CustomTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = "Description",
                    maxLines = 3,
                    onFocusChange = { hasTextFieldFocus = it }
                )

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CustomTextField(
                        value = duration,
                        onValueChange = { if (it.all { c -> c.isDigit() }) duration = it },
                        label = "Min",
                        modifier = Modifier.weight(1f),
                        onFocusChange = { hasTextFieldFocus = it }
                    )
                    CustomTextField(
                        value = releaseYear,
                        onValueChange = { if (it.all { c -> c.isDigit() }) releaseYear = it },
                        label = "Year",
                        modifier = Modifier.weight(1f),
                        onFocusChange = { hasTextFieldFocus = it }
                    )
                }

                CustomTextField(
                    value = posterUrl,
                    onValueChange = { posterUrl = it },
                    label = "Poster URL",
                    onFocusChange = { hasTextFieldFocus = it }
                )
                CustomTextField(
                    value = trailerUrl,
                    onValueChange = { trailerUrl = it },
                    label = "Trailer URL",
                    onFocusChange = { hasTextFieldFocus = it }
                )

                Text("Select Genres", color = Color.White.copy(0.7f), fontSize = 14.sp)

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    genres.forEach { genre ->
                        val isSelected = initialSelectedGenres.contains(genre.id)
                        val genreColor = genre.color.toColor()

                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (isSelected) initialSelectedGenres.remove(genre.id)
                                else initialSelectedGenres.add(genre.id)
                            },
                            label = { Text(genre.name) },
                            enabled = true,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = genreColor,
                                selectedLabelColor = Color.White,
                                containerColor = Color.Transparent,
                                labelColor = Color.White,
                                disabledContainerColor = Color.Transparent,
                                disabledLabelColor = Color.Gray
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = genreColor.copy(alpha = 0.5f),
                                selectedBorderColor = genreColor
                            )
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x10FFFFFF), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text("Premium Content", color = Color.White, modifier = Modifier.weight(1f))
                    Switch(
                        checked = isPremium,
                        onCheckedChange = { isPremium = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFFFFD700),
                            checkedTrackColor = Color(0x80FFD700)
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotEmpty()) {
                        isLoading = true
                        scope.launch {
                            saveMovie(
                                movie?.id,
                                title,
                                description,
                                trailerUrl,
                                "",
                                posterUrl,
                                duration.toIntOrNull() ?: 0,
                                releaseYear.toIntOrNull() ?: 2024,
                                isPremium,
                                initialSelectedGenres.toList()
                            )
                            onSave()
                            onDismiss()
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D4FF)),
                enabled = !isLoading
            ) {
                if (isLoading) CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                else Text("SAVE MOVIE")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text(
                    "CANCEL",
                    color = Color.White.copy(0.7f)
                )
            }
        }
    )
}

@Composable
fun CustomTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    onFocusChange: (Boolean) -> Unit = {}
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = Color.White.copy(0.5f)) },
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
                onFocusChange(focusState.isFocused)
            },
        maxLines = maxLines,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFF00D4FF),
            unfocusedBorderColor = Color.White.copy(0.2f),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = Color(0xFF00D4FF)
        ),
        shape = RoundedCornerShape(12.dp)
    )
}

// ==============================
// ADD GENRE DIALOG (Fixed Keyboard Handling)
// ==============================

@Composable
fun PremiumAddGenreDialog(onDismiss: () -> Unit, onSave: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val autoColors = listOf(
        "#F44336",
        "#E91E63",
        "#9C27B0",
        "#673AB7",
        "#3F51B5",
        "#2196F3",
        "#00BCD4",
        "#009688",
        "#4CAF50",
        "#FFC107",
        "#FF9800",
        "#FF5722"
    )

    // REMOVED: val focusManager...
    // REMOVED: var hasTextFieldFocus...
    // REMOVED: BackHandler { ... }
    // We let the native Android system handle the keyboard/back logic.

    AlertDialog(
        onDismissRequest = {
            // This runs when the user clicks outside or presses Back (when keyboard is closed)
            if (!isLoading) onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            // CRITICAL FIX: Set this to true.
            // Android automatically prioritizes closing the keyboard first.
            // If keyboard is closed, this property allows the dialog to close.
            dismissOnBackPress = true
        ),
        modifier = Modifier
            .fillMaxWidth(0.9f)
            .wrapContentHeight(),
        containerColor = Color(0xFF121225),
        title = { Text("Create Genre", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    "Enter the name of the new genre category.",
                    color = Color.White.copy(0.7f),
                    fontSize = 14.sp
                )
                CustomTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Genre Title",
                    modifier = Modifier.fillMaxWidth()
                    // REMOVED: onFocusChange
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotEmpty()) {
                        isLoading = true
                        scope.launch {
                            val randomColor = autoColors.random()
                            saveGenre(name, "", randomColor)
                            onSave()
                            onDismiss()
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0080)),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                } else {
                    Text("ADD GENRE")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text("CANCEL", color = Color.White.copy(0.7f))
            }
        }
    )
}

// ==============================
// UTILS & TABS
// ==============================

@Composable
fun GlassMorphicTabBar(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .height(60.dp)
            .background(Color(0xFF2A2A4A), RoundedCornerShape(20.dp))
            .border(1.dp, Color(0x50FFFFFF), RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
    ) {
        TabButton(
            text = "Movies",
            icon = Icons.Default.Theaters,
            selected = selectedTab == 0,
            color = Color(0xFF00D4FF),
            onClick = { onTabSelected(0) },
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(Color(0x20FFFFFF))
        )

        TabButton(
            text = "Genres",
            icon = Icons.Default.Category,
            selected = selectedTab == 1,
            color = Color(0xFFFF0080),
            onClick = { onTabSelected(1) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun TabButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClick = onClick)
            .background(if (selected) color.copy(0.2f) else Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = if (selected) color else Color.Gray)
            Text(
                text,
                color = if (selected) Color.White else Color.Gray,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun CosmicEmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    actionText: String,
    onAction: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .background(Color(0x10FFFFFF), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Color.White.copy(0.5f), modifier = Modifier.size(40.dp))
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            title,
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(description, color = Color.Gray, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onAction,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D4FF)),
            contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp)
        ) {
            Text(actionText)
        }
    }
}

// ==============================
// FIRESTORE LOGIC
// ==============================

suspend fun refreshData(onSuccess: (List<Movie>, List<Genre>) -> Unit, onError: () -> Unit) {
    withContext(Dispatchers.IO) {
        try {
            val db = Firebase.firestore
            val m = db.collection("movies").get().await().toObjects(Movie::class.java)
            val g = db.collection("genres").get().await().toObjects(Genre::class.java)
            withContext(Dispatchers.Main) { onSuccess(m, g) }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { onError() }
        }
    }
}

suspend fun saveMovie(
    id: String?,
    title: String,
    desc: String,
    trailer: String,
    full: String,
    poster: String,
    dur: Int,
    year: Int,
    prem: Boolean,
    genres: List<String>
) {
    withContext(Dispatchers.IO) {
        val db = Firebase.firestore
        val data = hashMapOf(
            "title" to title,
            "description" to desc,
            "trailerUrl" to trailer,
            "fullMovieUrl" to full,
            "posterUrl" to poster,
            "duration" to dur,
            "releaseYear" to year,
            "isPremium" to prem,
            "genreIds" to genres,
            "updatedAt" to Timestamp.now()
        )

        if (id == null) {
            data["createdAt"] = Timestamp.now()
            db.collection("movies").add(data).await()
        } else {
            // FIXED: Use SetOptions.merge() to update fields without deleting 'createdAt'
            db.collection("movies").document(id).set(data, SetOptions.merge()).await()
        }
    }
}

suspend fun deleteMovie(id: String) = withContext(Dispatchers.IO) {
    Firebase.firestore.collection("movies").document(id).delete().await()
}

suspend fun saveGenre(name: String, desc: String, color: String) = withContext(Dispatchers.IO) {
    Firebase.firestore.collection("genres").add(
        hashMapOf(
            "name" to name, "description" to desc, "color" to color,
            "movieCount" to 0, "createdAt" to Timestamp.now()
        )
    ).await()
}

suspend fun deleteGenre(id: String) = withContext(Dispatchers.IO) {
    Firebase.firestore.collection("genres").document(id).delete().await()
}