package com.mario.luna.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.mario.luna.data.SongRepository
import com.mario.luna.model.Song
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

    private var player: ExoPlayer? = null

    init {
        initializePlayer()
    }

    private fun initializePlayer() {
        player = ExoPlayer.Builder(getApplication()).build().apply {
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        _duration.value = duration
                    }
                    if (playbackState == Player.STATE_ENDED) {
                        playNext()
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _isPlaying.value = isPlaying
                }
            })
        }

        viewModelScope.launch {
            while (true) {
                if (_isPlaying.value) {
                    _progress.value = player?.currentPosition ?: 0L
                }
                delay(1000)
            }
        }
    }

    fun loadSongs() {
        viewModelScope.launch {
            val localSongs = repository.getLocalSongs()
            _songs.value = localSongs
        }
    }

    fun playSong(song: Song) {
        if (_currentSong.value?.id == song.id) {
            if (_isPlaying.value) pause() else play()
            return
        }

        _currentSong.value = song
        player?.setMediaItem(MediaItem.fromUri(song.contentUri))
        player?.prepare()
        player?.play()
    }

    fun play() {
        player?.play()
    }

    fun pause() {
        player?.pause()
    }

    fun playNext() {
        val currentList = _songs.value
        val current = _currentSong.value ?: return
        val index = currentList.indexOfFirst { it.id == current.id }
        if (index != -1 && index < currentList.size - 1) {
            playSong(currentList[index + 1])
        } else if (currentList.isNotEmpty()) {
             // Loop back to start or stop? Let's loop for now
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
        player?.seekTo(position)
        _progress.value = position
    }

    override fun onCleared() {
        super.onCleared()
        player?.release()
        player = null
    }
}