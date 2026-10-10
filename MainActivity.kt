package com.joylk63.nothingvideoplayer

import android.content.Intent
import android.graphics.Color
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs
import kotlin.math.roundToInt

@UnstableApi
class MainActivity : AppCompatActivity() {
    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var videoTitle: TextView
    private lateinit var nowPlayingLabel: TextView
    private lateinit var playlistCount: TextView
    private lateinit var gestureHint: TextView
    private lateinit var playlistAdapter: PlaylistAdapter
    private val videos = mutableListOf<VideoEntry>()
    private var currentIndex = -1
    private var speedIndex = 2
    private val speeds = floatArrayOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
    private val audioManager by lazy { getSystemService(AUDIO_SERVICE) as AudioManager }
    private var downX = 0f
    private var downY = 0f
    private var downBrightness = 0.5f
    private var downVolume = 0
    private var lastTapTime = 0L
    private var lastTapX = 0f
    private var gestureConsumed = false

    private val openVideos = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data ?: return@registerForActivityResult
            val clip = data.clipData
            val incoming = mutableListOf<Uri>()
            if (clip != null) {
                for (i in 0 until clip.itemCount) incoming.add(clip.getItemAt(i).uri)
            } else data.data?.let { incoming.add(it) }
            incoming.forEach { uri ->
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: Exception) { }
                if (videos.none { it.uri == uri }) videos.add(VideoEntry(uri, displayName(uri)))
            }
            playlistAdapter.notifyDataSetChanged()
            updatePlaylistCount()
            if (currentIndex == -1 && videos.isNotEmpty()) playAt(0)
        }
    }

    private val openSubtitle = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && currentIndex >= 0) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) { }
            val subtitle = MediaItem.SubtitleConfiguration.Builder(uri)
                .setMimeType(MimeTypes.APPLICATION_SUBRIP)
                .setLanguage("und")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
            val entry = videos[currentIndex]
            entry.externalSubtitle = subtitle
            playAt(currentIndex, keepPosition = true)
            Toast.makeText(this, "Subtitle attached", Toast.LENGTH_SHORT).show()
        } else if (currentIndex < 0) {
            Toast.makeText(this, "Select a video first", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(9, 9, 9)
        window.navigationBarColor = Color.rgb(9, 9, 9)
        setContentView(R.layout.activity_main)

        playerView = findViewById(R.id.playerView)
        videoTitle = findViewById(R.id.videoTitle)
        nowPlayingLabel = findViewById(R.id.nowPlayingLabel)
        playlistCount = findViewById(R.id.playlistCount)
        gestureHint = findViewById(R.id.gestureHint)

        player = ExoPlayer.Builder(this).build()
        playerView.player = player
        playerView.setShowSubtitleButton(true)
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentIndex = player.currentMediaItemIndex
                updateCurrentLabels()
                playlistAdapter.notifyDataSetChanged()
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && currentIndex < videos.lastIndex) playAt(currentIndex + 1)
            }
        })

        val recycler = findViewById<RecyclerView>(R.id.playlistView)
        recycler.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        playlistAdapter = PlaylistAdapter(videos, { playAt(it) }, { currentIndex })
        recycler.adapter = playlistAdapter

        findViewById<Button>(R.id.openVideosButton).setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "video/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
            openVideos.launch(intent)
        }
        findViewById<Button>(R.id.subtitleButton).setOnClickListener {
            if (currentIndex < 0) {
                Toast.makeText(this, "Select a video first", Toast.LENGTH_SHORT).show()
            } else openSubtitle.launch(arrayOf("application/x-subrip", "text/*", "application/octet-stream"))
        }
        findViewById<Button>(R.id.speedButton).setOnClickListener { showSpeedDialog() }
        setupGestures()
        updatePlaylistCount()
    }

    private fun playAt(index: Int, keepPosition: Boolean = false) {
        if (index !in videos.indices) return
        val oldPosition = if (keepPosition) player.currentPosition else 0L
        currentIndex = index
        val entry = videos[index]
        val itemBuilder = MediaItem.Builder().setUri(entry.uri).setMediaId(entry.uri.toString())
        entry.externalSubtitle?.let { itemBuilder.setSubtitleConfigurations(listOf(it)) }
        player.setMediaItem(itemBuilder.build())
        player.prepare()
        if (keepPosition) player.seekTo(oldPosition)
        player.playWhenReady = true
        updateCurrentLabels()
        playlistAdapter.notifyDataSetChanged()
    }

    private fun updateCurrentLabels() {
        val entry = videos.getOrNull(currentIndex)
        videoTitle.text = entry?.name ?: "No video selected"
        nowPlayingLabel.text = if (entry == null) "NOW PLAYING  /  SELECT A VIDEO"
            else "NOW PLAYING  /  %02d OF %02d".format(currentIndex + 1, videos.size)
        playlistAdapter.notifyDataSetChanged()
    }

    private fun updatePlaylistCount() {
        playlistCount.text = "%02d ITEMS".format(videos.size)
    }

    private fun showSpeedDialog() {
        val labels = speeds.map { "${it}×" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("PLAYBACK SPEED")
            .setSingleChoiceItems(labels, speedIndex) { dialog, which ->
                speedIndex = which
                player.setPlaybackSpeed(speeds[which])
                findViewById<Button>(R.id.speedButton).text = "${speeds[which]}×"
                dialog.dismiss()
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun setupGestures() {
        playerView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    downBrightness = window.attributes.screenBrightness.takeIf { it >= 0 } ?: 0.5f
                    downVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    gestureConsumed = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    if (abs(dy) > 28 && abs(dy) > abs(dx) * 1.15f) {
                        gestureConsumed = true
                        if (downX < playerView.width / 2f) {
                            val params = window.attributes
                            params.screenBrightness = (downBrightness - dy / playerView.height).coerceIn(0.05f, 1f)
                            window.attributes = params
                            showGestureHint("BRIGHTNESS  ${(params.screenBrightness * 100).roundToInt()}%")
                        } else {
                            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                            val value = (downVolume - dy / playerView.height * max).roundToInt().coerceIn(0, max)
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0)
                            showGestureHint("VOLUME  ${(value * 100f / max).roundToInt()}%")
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!gestureConsumed) {
                        val now = System.currentTimeMillis()
                        val x = event.x
                        if (now - lastTapTime < 300 && abs(x - lastTapX) < playerView.width * 0.25f) {
                            val delta = if (x < playerView.width / 2f) -10_000L else 10_000L
                            player.seekTo((player.currentPosition + delta).coerceAtLeast(0L))
                            showGestureHint(if (delta < 0) "−10 SECONDS" else "+10 SECONDS")
                            lastTapTime = 0L
                        } else {
                            lastTapTime = now
                            lastTapX = x
                            playerView.performClick()
                        }
                    }
                    true
                }
                else -> true
            }
        }
    }

    private fun showGestureHint(message: String) {
        gestureHint.text = message
        gestureHint.visibility = View.VISIBLE
        gestureHint.postDelayed({ gestureHint.visibility = View.GONE }, 850)
    }

    private fun displayName(uri: Uri): String {
        var name = "Video"
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) name = cursor.getString(0) ?: name
        }
        return name.substringBeforeLast('.', name)
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }

    data class VideoEntry(val uri: Uri, val name: String, var externalSubtitle: MediaItem.SubtitleConfiguration? = null)

}

@UnstableApi
private class PlaylistAdapter(
    private val items: List<MainActivity.VideoEntry>,
    private val onClick: (Int) -> Unit,
    private val currentIndex: () -> Int
) : RecyclerView.Adapter<PlaylistAdapter.Holder>() {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val index: TextView = view.findViewById(R.id.itemIndex)
        val title: TextView = view.findViewById(R.id.itemTitle)
        val status: TextView = view.findViewById(R.id.itemStatus)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): Holder {
        val view = android.view.LayoutInflater.from(parent.context).inflate(R.layout.item_playlist, parent, false)
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        val selected = position == currentIndex()
        holder.index.text = "%02d / VIDEO".format(position + 1)
        holder.title.text = item.name
        holder.status.text = if (selected) "● PLAYING" else "TAP TO PLAY"
        holder.status.setTextColor(if (selected) Color.rgb(229, 57, 53) else Color.LTGRAY)
        holder.itemView.setOnClickListener { onClick(position) }
        holder.itemView.alpha = if (selected) 1f else 0.82f
    }

    override fun getItemCount() = items.size
}
