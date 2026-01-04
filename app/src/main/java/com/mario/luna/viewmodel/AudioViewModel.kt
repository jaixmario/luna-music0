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
            
            // Sync initial state from service
            controller?.let { player ->
                if (player.isPlaying) {
                    _isPlaying.value = true
                    // Try to restore current song from media item
                    player.currentMediaItem?.let { item ->
                        val id = item.mediaId.toLongOrNull()
                        if (id != null) {
                             // We might not have songs loaded yet, so this might be tricky if we don't load songs first
                             // But we can update the ID at least or wait for songs to load
                             _currentSong.value = _songs.value.find { it.id == id }
                             // If songs are not loaded, this will remain null until they are, 
                             // and we'll need to check again after loading songs.
                        }
                    }
                }
            }
        }, MoreExecutors.directExecutor())
        
        // Polling for progress update
        viewModelScope.launch {
            while (true) {
                // Check if playing from controller directly as _isPlaying might be delayed
                if (controller?.isPlaying == true) {
                    _progress.value = controller?.currentPosition ?: 0L
                    _isPlaying.value = true // Ensure state matches
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
            
            // After loading songs, check if the service is already playing something and sync it
             controller?.currentMediaItem?.let { item ->
                val id = item.mediaId.toLongOrNull()
                if (id != null) {
                    val foundSong = localSongs.find { it.id == id }
                    if (foundSong != null) {
                        _currentSong.value = foundSong
                        _isPlaying.value = controller?.isPlaying == true
                         if (_isPlaying.value) {
                             _duration.value = controller?.duration ?: 0L
                         }
                    }
                }
            }
        }
    }

    fun playSong(song: Song) {
        val player = controller ?: return
        
        val currentList = _songs.value
        val startIndex = currentList.indexOfFirst { it.id == song.id }
        
        if (startIndex == -1) return

        if (_currentSong.value?.id == song.id) {
            if (_isPlaying.value) pause() else play()
            return
        }

        _currentSong.value = song
        
        // If we haven't set the playlist yet or it's empty, set it up. 
        // Or if we just want to restart the playlist order from the main list.
        // For simplicity, we re-set the media items to ensure order matches the list.
        // Optimization: Check if timeline matches _songs. 
        // But re-setting with same items is usually handled well by ExoPlayer diffing.
        
        val mediaItems = currentList.map { track ->
            val mediaMetadata = MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artist)
                .setAlbumTitle(track.album)
                .setArtworkUri(track.albumArtUri)
                .build()
            
            MediaItem.Builder()
                .setUri(track.contentUri)
                .setMediaId(track.id.toString())
                .setMediaMetadata(mediaMetadata)
                .build()
        }

        player.setMediaItems(mediaItems, startIndex, 0L)
        player.prepare()
        player.play()
    }
    
    fun playNext(song: Song) {
        val player = controller ?: return
        val count = player.mediaItemCount
        var fromIndex = -1
        
        // Find the index of the song to move
        for (i in 0 until count) {
            val item = player.getMediaItemAt(i)
            if (item.mediaId == song.id.toString()) {
                fromIndex = i
                break
            }
        }
        
        if (fromIndex != -1) {
            var targetIndex = player.currentMediaItemIndex + 1
            // If target is out of bounds (end of list), just move to end
            if (targetIndex >= count) {
                targetIndex = count - 1
            }
            
            // If we are moving the item from before the current position, 
            // the indices might shift. moveMediaItem handles this logic mostly, 
            // but we want it effectively at current + 1.
            
            player.moveMediaItem(fromIndex, targetIndex)
        }
    }
    
    fun addToQueue(song: Song) {
        val player = controller ?: return
        val count = player.mediaItemCount
        var fromIndex = -1
        
        for (i in 0 until count) {
            val item = player.getMediaItemAt(i)
            if (item.mediaId == song.id.toString()) {
                fromIndex = i
                break
            }
        }
        
        if (fromIndex != -1) {
            player.moveMediaItem(fromIndex, count - 1)
        }
    }

    fun play() {
        controller?.play()
    }

    fun pause() {
        controller?.pause()
    }

    fun playNext() {
        controller?.seekToNextMediaItem()
    }

    fun playPrevious() {
        controller?.seekToPreviousMediaItem()
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