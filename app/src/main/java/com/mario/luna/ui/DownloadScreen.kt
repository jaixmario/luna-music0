package com.mario.luna.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mario.luna.viewmodel.DownloadUIState
import com.mario.luna.viewmodel.DownloadViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(
    onDismiss: () -> Unit,
    onDownloadComplete: () -> Unit,
    downloadViewModel: DownloadViewModel = viewModel()
) {
    var url by remember { mutableStateOf("") }
    val uiState by downloadViewModel.uiState.collectAsState()
    val downloadProgress by downloadViewModel.downloadProgress.collectAsState()
    val errorMessage by downloadViewModel.errorMessage.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(uiState) {
        if (uiState == DownloadUIState.DONE) {
            delay(2000) // Wait for user to see the "Done" message
            onDownloadComplete()
            onDismiss()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        AnimatedContent(
            targetState = uiState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            label = "DownloadStateAnimation"
        ) { state ->
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                when (state) {
                    DownloadUIState.IDLE, DownloadUIState.ERROR -> {
                        Text("Download Song", style = MaterialTheme.typography.headlineSmall)
                        Spacer(modifier = Modifier.height(24.dp))
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            label = { Text("YouTube URL") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { downloadViewModel.downloadFile(url) },
                            enabled = url.isNotBlank() && (url.contains("youtube.com") || url.contains("youtu.be"))
                        ) {
                            Text("Download")
                        }
                        
                        errorMessage?.let {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(it, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    DownloadUIState.DOWNLOADING -> {
                        CircularProgressIndicator(progress = { downloadProgress })
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Downloading... ${(downloadProgress * 100).toInt()}%", style = MaterialTheme.typography.bodyLarge)
                    }
                    DownloadUIState.DONE -> {
                        Icon(Icons.Default.CheckCircle, contentDescription = "Complete", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Download Complete!", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}