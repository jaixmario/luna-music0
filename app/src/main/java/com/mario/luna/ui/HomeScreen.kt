package com.mario.luna.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.lifecycle.viewmodel.compose.viewModel
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

    val context = LocalContext.current
    
    // Permission handling
    var hasPermission by remember { mutableStateOf(false) }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasPermission = isGranted
        if (isGranted) {
            viewModel.loadSongs()
        }
    }

    LaunchedEffect(Unit) {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        permissionLauncher.launch(permission)
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { 
                    Text(
                        "Library",
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.Bold
                        )
                    ) 
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
                        onTogglePlay = {
                            if (isPlaying) viewModel.pause() else viewModel.play()
                        },
                        modifier = Modifier
                            .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
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