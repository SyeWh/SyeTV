package com.sye.tv

import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.C
import androidx.media3.common.MediaItem as M3Item
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.sye.tv.databinding.ActivityPlayerBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URL   = "url"
        const val EXTRA_TITLE = "title"
        const val EXTRA_DRIVE = "is_drive"
    }

    private lateinit var binding: ActivityPlayerBinding
    private var player: ExoPlayer? = null
    private lateinit var trackSelector: DefaultTrackSelector

    private val handler = Handler(Looper.getMainLooper())
    private var controlsVisible = false
    private var isDrive = false

    /* ── Runnables ─────────────────────────────────────────────── */

    private val hideControls = Runnable {
        binding.controlsOverlay.visibility = View.GONE
        controlsVisible = false
    }

    private val tickProgress = object : Runnable {
        override fun run() {
            updateProgress()
            handler.postDelayed(this, 500)
        }
    }

    /* ── Lifecycle ─────────────────────────────────────────────── */

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val url   = intent.getStringExtra(EXTRA_URL)   ?: return finish()
        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        isDrive   = intent.getBooleanExtra(EXTRA_DRIVE, false)

        binding.tvPlayerTitle.text = title
        binding.tvQuality.visibility = if (isDrive) View.VISIBLE else View.GONE

        buildPlayer()
        resolveAndPlay(url)

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                if (fromUser) player?.seekTo(p.toLong())
            }
            override fun onStartTrackingTouch(sb: SeekBar) = showControls()
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        binding.tvQuality.setOnClickListener { showQualityDialog() }
    }

    override fun onPause()   { super.onPause();   player?.pause() }
    override fun onResume()  { super.onResume();  player?.play()  }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
        super.onDestroy()
    }

    /* ── Player setup ──────────────────────────────────────────── */

    private fun buildPlayer() {
        trackSelector = DefaultTrackSelector(this)

        player = ExoPlayer.Builder(this)
            .setTrackSelector(trackSelector)
            .build()
            .also { exo ->
                binding.playerView.player = exo
                binding.playerView.useController = false

                exo.addListener(object : Player.Listener {

                    override fun onPlaybackStateChanged(state: Int) {
                        when (state) {
                            Player.STATE_BUFFERING -> {
                                binding.progressBuffering.visibility = View.VISIBLE
                            }
                            Player.STATE_READY -> {
                                binding.progressBuffering.visibility = View.GONE
                                handler.post(tickProgress)
                                showControls()
                            }
                            Player.STATE_ENDED -> finish()
                            else -> {}
                        }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        binding.tvPlayPauseIcon.text = if (isPlaying) "⏸" else "▶"
                    }
                })
            }
    }

    /* ── URL resolution ────────────────────────────────────────── */

    private fun resolveAndPlay(rawUrl: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val finalUrl = if (DriveHelper.isDrive(rawUrl)) {
                resolveDriveUrl(DriveHelper.streamUrl(rawUrl))
            } else rawUrl

            withContext(Dispatchers.Main) {
                player?.run {
                    setMediaItem(M3Item.fromUri(finalUrl))
                    prepare()
                    play()
                }
            }
        }
    }

    /**
     * Follow up to two redirects so ExoPlayer gets the real stream URL.
     * Drive sometimes redirects once (cookie-less large-file path).
     */
    private fun resolveDriveUrl(url: String): String {
        var current = url
        repeat(2) {
            try {
                val conn = URL(current).openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = false
                conn.requestMethod = "HEAD"
                conn.connectTimeout = 6_000
                conn.readTimeout   = 6_000
                conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android 10; TV) AppleWebKit/537.36")
                conn.connect()
                val loc = conn.getHeaderField("Location")
                conn.disconnect()
                if (!loc.isNullOrBlank()) current = loc else return current
            } catch (_: Exception) { return current }
        }
        return current
    }

    /* ── Controls ──────────────────────────────────────────────── */

    private fun showControls() {
        binding.controlsOverlay.visibility = View.VISIBLE
        controlsVisible = true
        handler.removeCallbacks(hideControls)
        handler.postDelayed(hideControls, 3_500)
    }

    private fun flashPlayPause() {
        binding.playPauseFlash.visibility = View.VISIBLE
        handler.postDelayed({ binding.playPauseFlash.visibility = View.GONE }, 700)
    }

    private fun flashSeek(back: Boolean) {
        val v = if (back) binding.tvSeekBack else binding.tvSeekFwd
        v.visibility = View.VISIBLE
        handler.postDelayed({ v.visibility = View.GONE }, 600)
    }

    private fun updateProgress() {
        val p = player ?: return
        val dur = p.duration.coerceAtLeast(1L)
        val pos = p.currentPosition
        binding.seekBar.max      = dur.toInt()
        binding.seekBar.progress = pos.toInt()
        binding.tvCurrentTime.text = ms(pos)
        binding.tvDuration.text    = ms(dur)
    }

    private fun ms(ms: Long): String {
        val s = ms / 1000
        val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec)
        else               "%d:%02d".format(m, sec)
    }

    /* ── Quality picker ────────────────────────────────────────── */

    private fun showQualityDialog() {
        val p = player ?: return
        val groups = p.currentTracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }

        if (groups.isEmpty()) {
            AlertDialog.Builder(this, R.style.DialogTheme)
                .setMessage("No alternative quality tracks available for this stream.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        // Build label list
        val labels = mutableListOf("Auto")
        val overrides = mutableListOf<TrackSelectionOverride?>()
        overrides.add(null)  // slot 0 = Auto

        for (group in groups) {
            for (ti in 0 until group.length) {
                val fmt = group.getTrackFormat(ti)
                val label = when {
                    fmt.height > 0 -> "${fmt.height}p"
                    fmt.bitrate > 0 -> "${fmt.bitrate / 1000} kbps"
                    else -> "Track $ti"
                }
                labels.add(label)
                overrides.add(TrackSelectionOverride(group.mediaTrackGroup, ti))
            }
        }

        AlertDialog.Builder(this, R.style.DialogTheme)
            .setTitle("Select Quality")
            .setItems(labels.toTypedArray()) { _, which ->
                val override = overrides[which]
                if (override == null) {
                    // Auto
                    trackSelector.setParameters(
                        trackSelector.buildUponParameters().clearOverrides()
                    )
                    binding.tvQuality.text = "Auto"
                } else {
                    trackSelector.setParameters(
                        trackSelector.buildUponParameters().setOverrideForType(override)
                    )
                    binding.tvQuality.text = labels[which]
                }
            }
            .show()
    }

    /* ── D-pad input ───────────────────────────────────────────── */

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val p = player ?: return super.onKeyDown(keyCode, event)

        return when (keyCode) {

            // Play / Pause
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                if (!controlsVisible) { showControls() }
                else {
                    if (p.isPlaying) p.pause() else p.play()
                    flashPlayPause()
                    showControls()
                }
                true
            }

            KeyEvent.KEYCODE_MEDIA_PLAY  -> { p.play();  flashPlayPause(); showControls(); true }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> { p.pause(); flashPlayPause(); showControls(); true }

            // Seek back 10 s
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                p.seekTo((p.currentPosition - 10_000L).coerceAtLeast(0))
                flashSeek(back = true)
                showControls()
                true
            }

            // Seek forward 10 s
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                p.seekTo(p.currentPosition + 10_000L)
                flashSeek(back = false)
                showControls()
                true
            }

            // Show controls / toggle
            KeyEvent.KEYCODE_DPAD_UP   -> { showControls(); true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { if (controlsVisible) hideControls.run() else showControls(); true }

            // Quality (menu key shortcut)
            KeyEvent.KEYCODE_MENU -> { if (isDrive) showQualityDialog(); true }

            // Back = exit
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_ESCAPE -> { finish(); true }

            else -> super.onKeyDown(keyCode, event)
        }
    }
}
