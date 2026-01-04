package com.mario.luna.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mario.luna.data.SettingsManager
import com.mario.luna.ui.components.FullScreenPlayer
import com.mario.luna.ui.components.MiniPlayer
import com.mario.luna.ui.components.MusicListItem
import com.mario.luna.viewmodel.AudioViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: AudioViewModel = viewModel()
) {
    val songs by viewModel.songs.collectAsState()
    val currentSong by viewModel.currentSong.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val duration by viewModel.duration.collectAsState()

    val context = LocalContext.current
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val userName by settingsManager.userName.collectAsState()
    
    // Permission handling
    var hasPermission by remember { mutableStateOf(false) }
    
    // Full screen player state
    var showFullScreenPlayer by remember { mutableStateOf(false) }
    
    // Settings state
    var showSettings by remember { mutableStateOf(false) }
    
    // Welcome Dialog state
    var showWelcomeDialog by remember { mutableStateOf(false) }
    
    // Check if name is set on first load
    LaunchedEffect(Unit) {
        if (settingsManager.getUserName().isEmpty()) {
            showWelcomeDialog = true
        }
    }

    if (showWelcomeDialog) {
        var tempName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { /* Force user to enter name */ },
            title = { Text("Welcome to Luna") },
            text = {
                Column {
                    Text("Please enter your name to get started.")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tempName,
                        onValueChange = { tempName = it },
                        label = { Text("Name") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (tempName.isNotBlank()) {
                            settingsManager.setUserName(tempName)
                            showWelcomeDialog = false
                        }
                    },
                    enabled = tempName.isNotBlank()
                ) {
                    Text("Continue")
                }
            }
        )
    }
    
    // Handle back button when player is open
    BackHandler(enabled = showFullScreenPlayer || showSettings) {
        if (showSettings) {
            showSettings = false
        } else {
            showFullScreenPlayer = false
        }
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (isGranted) {
            viewModel.loadSongs()
        }
    }
    
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        // Notification permission granted or denied, not critical for app function but good for controls
    }

    LaunchedEffect(Unit) {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
             hasPermission = true
             viewModel.loadSongs()
        } else {
             permissionLauncher.launch(permission)
        }
        
        // Request notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    if (showSettings) {
        SettingsScreen(onDismiss = { showSettings = false })
    } else if (showFullScreenPlayer && currentSong != null) {
        FullScreenPlayer(
            song = currentSong!!,
            isPlaying = isPlaying,
            progress = progress,
            duration = duration,
            onTogglePlay = { if (isPlaying) viewModel.pause() else viewModel.play() },
            onNext = { viewModel.playNext() },
            onPrevious = { viewModel.playPrevious() },
            onSeek = { viewModel.seekTo(it) },
            onDismiss = { showFullScreenPlayer = false }
        )
    } else {
        Scaffold(
            topBar = {
                LargeTopAppBar(
                    title = { 
                        Column {
                            if (userName.isNotEmpty()) {
                                Text(
                                    text = "Hey $userName,",
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                             Text(
                                "Library",
                                style = MaterialTheme.typography.displaySmall.copy(
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    },
                    colors = TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                if (currentSong != null) {
                    // Use WindowInsets to add padding for the system navigation bar
                    val navigationBarInsets = WindowInsets.navigationBars.asPaddingValues()
                    Box(
                        modifier = Modifier.padding(navigationBarInsets)
                    ) {
                        MiniPlayer(
                            song = currentSong!!,
                            isPlaying = isPlaying,
                            progress = if (duration > 0) progress.toFloat() / duration.toFloat() else 0f,
                            onTogglePlay = {
                                if (isPlaying) viewModel.pause() else viewModel.play()
                            },
                            onNext = { viewModel.playNext() },
                            onPrevious = { viewModel.playPrevious() },
                            modifier = Modifier
                                .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
                                .clickable { showFullScreenPlayer = true }
                        )
                    }
                }
            },
            contentWindowInsets = WindowInsets.systemBars
        ) { innerPadding ->
            if (!hasPermission) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Please grant permission to access audio files.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    items(songs) { song ->
                        val isCurrentSong = currentSong?.id == song.id
                        MusicListItem(
                            song = song,
                            isPlaying = isCurrentSong && isPlaying,
                            onClick = { viewModel.playSong(song) }
                        )
                        Divider(
                            color = Color.LightGray.copy(alpha = 0.2f),
                            modifier = Modifier.padding(start = 88.dp) // Indent divider like iOS
                        )
                    }
                    
                    // Add some bottom padding so the last item isn't covered by the mini player if present
                    item {
                        Spacer(modifier = Modifier.height(100.dp))
                    }
                }
            }
        }
    }
}