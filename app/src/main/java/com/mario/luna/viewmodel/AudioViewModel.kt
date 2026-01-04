package com.mario.luna.viewmodel

import android.app.Application
import android.content.ComponentName
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.mario.luna.data.SongRepository
import com.mario.luna.model.Song
import com.mario.luna.service.PlaybackService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AudioViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SongRepository(application)
    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _progress = MutableStateFlow(0L)
    val progress: StateFlow<Long> = _progress.asStateFlow()
    
    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    init {
        initializeController()
    }

    private fun initializeController() {
        val sessionToken = SessionToken(
            getApplication(),
            ComponentName(getApplication(), PlaybackService::class.java)
        )
        controllerFuture = MediaController.Builder(getApplication(), sessionToken).buildAsync()
        controllerFuture?.addListener({
            controller = controllerFuture?.get()
            setupPlayerListener()
        }, MoreExecutors.directExecutor())
        
        // Polling for progress update
        viewModelScope.launch {
            while (true) {
                if (_isPlaying.value) {
                    _progress.value = controller?.currentPosition ?: 0L
                }
                delay(1000)
            }
        }
    }
    
    private fun setupPlayerListener() {
        controller?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _duration.value = controller?.duration ?: 0L
                }
                if (playbackState == Player.STATE_ENDED) {
                    playNext()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }
            
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)
                // When media item changes (e.g. from notification "Next"), update currentSong
                if (mediaItem != null) {
                   val id = mediaItem.mediaId.toLongOrNull()
                   if (id != null) {
                       // Find song by ID from loaded songs
                       val foundSong = _songs.value.find { it.id == id }
                       if (foundSong != null) {
                           _currentSong.value = foundSong
                       }
                   }
                }
            }
        })
    }

    fun loadSongs() {
        viewModelScope.launch {
            val localSongs = repository.getLocalSongs()
            _songs.value = localSongs
            // If we have songs and player is ready, maybe set playlist? 
            // For now we just load them into memory.
        }
    }

    fun playSong(song: Song) {
        val player = controller ?: return
        
        if (_currentSong.value?.id == song.id) {
            if (_isPlaying.value) pause() else play()
            return
        }

        _currentSong.value = song
        
        val mediaMetadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setAlbumTitle(song.album)
            .setArtworkUri(song.albumArtUri)
            .build()
            
        val mediaItem = MediaItem.Builder()
            .setUri(song.contentUri)
            .setMediaId(song.id.toString())
            .setMediaMetadata(mediaMetadata)
            .build()

        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
    }

    fun play() {
        controller?.play()
    }

    fun pause() {
        controller?.pause()
    }

    fun playNext() {
        val currentList = _songs.value
        val current = _currentSong.value ?: return
        val index = currentList.indexOfFirst { it.id == current.id }
        if (index != -1 && index < currentList.size - 1) {
            playSong(currentList[index + 1])
        } else if (currentList.isNotEmpty()) {
             playSong(currentList[0])
        }
    }

    fun playPrevious() {
        val currentList = _songs.value
        val current = _currentSong.value ?: return
        val index = currentList.indexOfFirst { it.id == current.id }
        if (index > 0) {
            playSong(currentList[index - 1])
        } else if (currentList.isNotEmpty()){
            playSong(currentList.last())
        }
    }

    fun seekTo(position: Long) {
        controller?.seekTo(position)
        _progress.value = position
    }

    override fun onCleared() {
        super.onCleared()
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }
}