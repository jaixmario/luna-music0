package com.mario.luna.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.IntentSender
import android.provider.MediaStore
import androidx.activity.result.IntentSenderRequest
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    private val _deleteRequest = MutableStateFlow<IntentSenderRequest?>(null)
    val deleteRequest: StateFlow<IntentSenderRequest?> = _deleteRequest.asStateFlow()

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
            
            controller?.let { player ->
                if (player.isPlaying) {
                    _isPlaying.value = true
                    player.currentMediaItem?.let { item ->
                        val id = item.mediaId.toLongOrNull()
                        if (id != null) {
                             _currentSong.value = _songs.value.find { it.id == id }
                        }
                    }
                }
            }
        }, MoreExecutors.directExecutor())
        
        viewModelScope.launch {
            while (true) {
                if (controller?.isPlaying == true) {
                    _progress.value = controller?.currentPosition ?: 0L
                    _isPlaying.value = true
                }
                delay(200) // Smoother progress updates
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
                if (mediaItem != null) {
                   val id = mediaItem.mediaId.toLongOrNull()
                   if (id != null) {
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
        
        val mediaItems = currentList.map { it.toMediaItem() }

        player.setMediaItems(mediaItems, startIndex, 0L)
        player.prepare()
        player.play()
    }
    
    fun playNext(song: Song) {
        val player = controller ?: return
        val fromIndex = _songs.value.indexOfFirst { it.id == song.id }
        if (fromIndex == -1) return

        val currentPlayingIndex = player.currentMediaItemIndex
        if (currentPlayingIndex == -1) return

        val toIndex = currentPlayingIndex + 1
        player.moveMediaItem(fromIndex, toIndex)

        _songs.value = _songs.value.toMutableList().apply {
            val itemToMove = removeAt(fromIndex)
            add(if (fromIndex < toIndex) toIndex - 1 else toIndex, itemToMove)
        }
    }

    fun addToQueue(song: Song) {
        val player = controller ?: return
        val fromIndex = _songs.value.indexOfFirst { it.id == song.id }
        if (fromIndex == -1) return

        val toIndex = player.mediaItemCount
        player.moveMediaItem(fromIndex, toIndex)

        _songs.value = _songs.value.toMutableList().apply {
            val itemToMove = removeAt(fromIndex)
            add(itemToMove)
        }
    }

    fun deleteSong(song: Song) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val intentSender = MediaStore.createDeleteRequest(
                    getApplication<Application>().contentResolver,
                    listOf(song.contentUri)
                ).intentSender
                
                withContext(Dispatchers.Main) {
                    _deleteRequest.value = IntentSenderRequest.Builder(intentSender).build()
                }

            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    fun onDeletionComplete(isDeleted: Boolean, song: Song) {
        if (isDeleted) {
            val currentList = _songs.value.toMutableList()
            currentList.remove(song)
            _songs.value = currentList

            if (_currentSong.value?.id == song.id) {
                controller?.stop()
                _currentSong.value = null
            }

            for (i in 0 until (controller?.mediaItemCount ?: 0)) {
                if (controller?.getMediaItemAt(i)?.mediaId == song.id.toString()) {
                    controller?.removeMediaItem(i)
                    break
                }
            }
        }
        _deleteRequest.value = null
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

    private fun Song.toMediaItem(): MediaItem {
        val mediaMetadata = MediaMetadata.Builder()
            .setTitle(this.title)
            .setArtist(this.artist)
            .setAlbumTitle(this.album)
            .setArtworkUri(this.albumArtUri)
            .build()
        
        return MediaItem.Builder()
            .setUri(this.contentUri)
            .setMediaId(this.id.toString())
            .setMediaMetadata(mediaMetadata)
            .build()
    }

    override fun onCleared() {
        super.onCleared()
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }
}