package com.mario.luna.viewmodel

import android.app.Application
import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mario.luna.data.SettingsManager
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder

enum class DownloadUIState { IDLE, DOWNLOADING, DONE, ERROR }

class DownloadViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(DownloadUIState.IDLE)
    val uiState: StateFlow<DownloadUIState> = _uiState.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress: StateFlow<Float> = _downloadProgress.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val settingsManager = SettingsManager.getInstance(application)

    private val ktorClient = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = 60000 // 1 minute
            connectTimeoutMillis = 20000 // 20 seconds
            socketTimeoutMillis = 60000 // 1 minute
        }
    }

    fun downloadFile(url: String) {
        viewModelScope.launch {
            _uiState.value = DownloadUIState.DOWNLOADING
            _downloadProgress.value = 0f
            _errorMessage.value = null
            try {
                val encodedUrl = URLEncoder.encode(url, "UTF-8")
                val baseUrl = settingsManager.getDownloadServerUrl()
                val downloadUrl = "$baseUrl/download?url=$encodedUrl"

                val httpResponse: HttpResponse = ktorClient.get(downloadUrl)

                if (httpResponse.status.isSuccess()) {
                    val serverFileName = httpResponse.headers[HttpHeaders.ContentDisposition]?.let {
                        ContentDisposition.parse(it).parameters.find { p -> p.name == "filename" }?.value?.removeSurrounding("\"")
                    }

                    val channel: ByteReadChannel = httpResponse.bodyAsChannel()
                    val contentLength = httpResponse.contentLength() ?: -1L
                    var bytesCopied = 0L

                    val success = withContext(Dispatchers.IO) {
                        val fileName = if (!serverFileName.isNullOrBlank()) serverFileName else "luna_song_${System.currentTimeMillis()}.mp3"
                        
                        val contentResolver = getApplication<Application>().contentResolver
                        val contentValues = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                            put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg")
                            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MUSIC)
                        }

                        val uri = contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)

                        if (uri != null) {
                            contentResolver.openOutputStream(uri)?.use { outputStream ->
                                val buffer = ByteArray(8192)
                                while (true) {
                                    val bytesRead = channel.readAvailable(buffer)
                                    if (bytesRead <= 0) break
                                    outputStream.write(buffer, 0, bytesRead)
                                    bytesCopied += bytesRead
                                    if (contentLength > 0) {
                                        _downloadProgress.value = (bytesCopied.toFloat() / contentLength.toFloat())
                                    }
                                }
                            }
                            true // Success
                        } else {
                            _errorMessage.value = "Failed to create file in MediaStore."
                            false // Failure
                        }
                    }

                    _uiState.value = if (success) DownloadUIState.DONE else DownloadUIState.ERROR

                } else {
                    _errorMessage.value = "Server error: ${httpResponse.status}"
                    _uiState.value = DownloadUIState.ERROR
                }
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "An unknown network error occurred."
                _uiState.value = DownloadUIState.ERROR
            }
        }
    }

    fun resetState() {
        _uiState.value = DownloadUIState.IDLE
        _downloadProgress.value = 0f
        _errorMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        ktorClient.close()
    }
}