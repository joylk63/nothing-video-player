package com.joy.menothingplayer

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.compose.ui.viewinterop.AndroidView
import java.util.Locale

private val Black = Color(0xFF080909)
private val Panel = Color(0xFF151717)
private val Panel2 = Color(0xFF202323)
private val White = Color(0xFFF4F4F0)
private val Muted = Color(0xFF929795)
private val Red = Color(0xFFE9473F)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(8, 9, 9)
        window.navigationBarColor = android.graphics.Color.rgb(8, 9, 9)
        setContent { MaterialTheme(colorScheme = darkColorScheme(background = Black, surface = Panel, primary = White)) { PlayerApp() } }
    }
}

data class VideoEntry(val uri: Uri, val title: String, val subtitleUri: Uri? = null)

@Composable
private fun PlayerApp() {
    val context = LocalContext.current
    var videos by remember { mutableStateOf(listOf<VideoEntry>()) }
    var selected by remember { mutableStateOf<VideoEntry?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var subtitleEnabled by remember { mutableStateOf(true) }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            val additions = uris.map { uri ->
                runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                VideoEntry(uri, uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: "Video")
            }
            videos = (videos + additions).distinctBy { it.uri }
            if (selected == null) selected = additions.firstOrNull()
        }
    }
    val subtitlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && selected != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val updated = selected!!.copy(subtitleUri = uri)
            videos = videos.map { if (it.uri == updated.uri) updated else it }
            selected = updated
        }
    }

    BackHandler(enabled = selected != null || showSettings) {
        if (showSettings) showSettings = false else selected = null
    }

    if (selected == null) {
        LibraryScreen(
            videos = videos,
            onAdd = { videoPicker.launch(arrayOf("video/*")) },
            onPlay = { selected = it },
            onSettings = { showSettings = true }
        )
    } else {
        PlayerScreen(
            entry = selected!!,
            playlist = videos,
            speed = playbackSpeed,
            subtitleEnabled = subtitleEnabled,
            onBack = { selected = null },
            onNext = { entry -> selected = entry },
            onSpeed = { playbackSpeed = it },
            onSubtitle = { subtitlePicker.launch(arrayOf("text/*", "application/x-subrip", "application/octet-stream")) }
        )
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            containerColor = Panel,
            title = { Text("Player settings", color = White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Playback", color = Muted)
                    listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { playbackSpeed = speed }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${speed}×", color = White, modifier = Modifier.weight(1f))
                            if (playbackSpeed == speed) Icon(Icons.Default.Check, null, tint = Red)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Subtitles enabled", color = White, modifier = Modifier.weight(1f))
                        Switch(checked = subtitleEnabled, onCheckedChange = { subtitleEnabled = it })
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSettings = false }) { Text("DONE", color = White) } }
        )
    }
}

@Composable
private fun LibraryScreen(videos: List<VideoEntry>, onAdd: () -> Unit, onPlay: (VideoEntry) -> Unit, onSettings: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Black).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("ME NOTHING", color = White, fontSize = 11.sp, letterSpacing = 4.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("Video Player", color = White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = onSettings, modifier = Modifier.background(Panel, CircleShape)) { Icon(Icons.Default.Settings, "Settings", tint = White) }
        }
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("YOUR LIBRARY", color = Muted, fontSize = 11.sp, letterSpacing = 2.sp, modifier = Modifier.weight(1f))
            Text("${videos.size} VIDEOS", color = Muted, fontSize = 11.sp, letterSpacing = 1.sp)
        }
        Spacer(Modifier.height(12.dp))
        if (videos.isEmpty()) {
            Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(Modifier.size(100.dp).clip(RoundedCornerShape(30.dp)).background(Panel), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.VideoLibrary, null, tint = White, modifier = Modifier.size(42.dp))
                }
                Spacer(Modifier.height(22.dp))
                Text("Your videos, in one place.", color = White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text("Add videos from your device to get started.", color = Muted, fontSize = 13.sp)
                Spacer(Modifier.height(22.dp))
                Button(onClick = onAdd, colors = ButtonDefaults.buttonColors(containerColor = White, contentColor = Black), shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("ADD VIDEOS", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
                items(videos, key = { it.uri.toString() }) { video ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel).clickable { onPlay(video) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(62.dp).clip(RoundedCornerShape(14.dp)).background(Panel2), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.PlayArrow, null, tint = White, modifier = Modifier.size(30.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(video.title, color = White, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(5.dp))
                            Text(if (video.subtitleUri != null) "SUBTITLES ADDED" else "LOCAL VIDEO", color = Muted, fontSize = 10.sp, letterSpacing = 1.2.sp)
                        }
                        Icon(Icons.Default.PlayArrow, null, tint = Red)
                    }
                }
            }
            Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp).height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = White, contentColor = Black), shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("ADD MORE VIDEOS", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        }
        Text("DESIGNED FOR THE MOMENT.", color = Color(0xFF5E6461), fontSize = 9.sp, letterSpacing = 2.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp))
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun PlayerScreen(
    entry: VideoEntry,
    playlist: List<VideoEntry>,
    speed: Float,
    subtitleEnabled: Boolean,
    onBack: () -> Unit,
    onNext: (VideoEntry) -> Unit,
    onSpeed: (Float) -> Unit,
    onSubtitle: () -> Unit
) {
    val context = LocalContext.current
    val exoPlayer = remember(entry.uri, entry.subtitleUri) {
        ExoPlayer.Builder(context).build().apply {
            val itemBuilder = MediaItem.Builder().setUri(entry.uri)
            entry.subtitleUri?.let { subtitle ->
                val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(subtitle)
                    .setMimeType("application/x-subrip")
                    .setLanguage("und")
                    .setSelectionFlags(androidx.media3.common.C.SELECTION_FLAG_DEFAULT)
                    .build()
                itemBuilder.setSubtitleConfigurations(listOf(subtitleConfig))
            }
            setMediaItem(itemBuilder.build())
            prepare()
            playWhenReady = true
        }
    }
    var isPlaying by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(true) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showPlaylist by remember { mutableStateOf(false) }
    var dragTotal by remember { mutableFloatStateOf(0f) }
    var seekToast by remember { mutableStateOf("") }

    DisposableEffect(exoPlayer) { onDispose { exoPlayer.release() } }
    LaunchedEffect(speed) { exoPlayer.setPlaybackSpeed(speed) }
    LaunchedEffect(subtitleEnabled, entry.uri) {
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, !subtitleEnabled)
            .build()
    }
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    Column(Modifier.fillMaxSize().background(Black).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = White) }
            Column(Modifier.weight(1f)) {
                Text("NOW PLAYING", color = Muted, fontSize = 9.sp, letterSpacing = 2.sp)
                Text(entry.title, color = White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box {
                IconButton(onClick = { showSpeedMenu = true }) { Text("${speed}×", color = White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                DropdownMenu(expanded = showSpeedMenu, onDismissRequest = { showSpeedMenu = false }, modifier = Modifier.background(Panel)) {
                    listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { value ->
                        DropdownMenuItem(text = { Text("${value}×", color = White) }, onClick = { onSpeed(value); showSpeedMenu = false })
                    }
                }
            }
            IconButton(onClick = onSubtitle) { Icon(Icons.Default.Subtitles, "Add subtitles", tint = if (entry.subtitleUri != null) Red else White) }
            IconButton(onClick = { showPlaylist = !showPlaylist }) { Icon(Icons.Default.MoreVert, "Playlist", tint = White) }
        }

        Box(Modifier.fillMaxWidth().weight(1f).background(Color.Black), contentAlignment = Alignment.Center) {
            AndroidView(
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                factory = { ctx -> PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                } },
                update = { it.player = exoPlayer }
            )
            Row(
                Modifier.fillMaxSize().pointerInput(entry.uri) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragTotal = 0f },
                        onHorizontalDrag = { _, delta -> dragTotal += delta },
                        onDragEnd = {
                            if (kotlin.math.abs(dragTotal) > 50f) {
                                val offset = if (dragTotal > 0) 10_000L else -10_000L
                                exoPlayer.seekTo((exoPlayer.currentPosition + offset).coerceAtLeast(0L))
                                seekToast = if (offset > 0) "+10 seconds" else "−10 seconds"
                            } else showControls = !showControls
                        }
                    )
                },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                AnimatedVisibility(showControls) { IconButton(onClick = { exoPlayer.seekBack(); seekToast = "−10 seconds" }, modifier = Modifier.size(54.dp).background(Color(0x66000000), CircleShape)) { Icon(Icons.Default.Replay10, null, tint = White, modifier = Modifier.size(32.dp)) } }
                AnimatedVisibility(showControls) { IconButton(onClick = { if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play() }, modifier = Modifier.size(70.dp).background(Color(0xAAE9473F), CircleShape)) { Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = White, modifier = Modifier.size(40.dp)) } }
                AnimatedVisibility(showControls) { IconButton(onClick = { exoPlayer.seekForward(); seekToast = "+10 seconds" }, modifier = Modifier.size(54.dp).background(Color(0x66000000), CircleShape)) { Icon(Icons.Default.Forward10, null, tint = White, modifier = Modifier.size(32.dp)) } }
            }
            if (seekToast.isNotEmpty()) {
                Text(seekToast, color = White, fontSize = 12.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xAA000000)).padding(horizontal = 14.dp, vertical = 8.dp))
            }
        }

        Column(Modifier.fillMaxWidth().background(Panel).padding(horizontal = 18.dp, vertical = 16.dp)) {
            Text(entry.title, color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(5.dp))
            Text("${if (isPlaying) "PLAYING" else "PAUSED"}  ·  ${speed}× SPEED", color = Muted, fontSize = 10.sp, letterSpacing = 1.4.sp)
            Spacer(Modifier.height(18.dp))
            var position by remember { mutableLongStateOf(0L) }
            var duration by remember { mutableLongStateOf(0L) }
            LaunchedEffect(exoPlayer) {
                while (true) {
                    position = exoPlayer.currentPosition.coerceAtLeast(0L)
                    duration = exoPlayer.duration.takeIf { it > 0 } ?: 0L
                    kotlinx.coroutines.delay(500)
                }
            }
            Slider(value = if (duration > 0) position.toFloat() / duration else 0f, onValueChange = { value -> position = (value * duration).toLong() }, onValueChangeFinished = { if (duration > 0) exoPlayer.seekTo(position) }, colors = SliderDefaults.colors(thumbColor = Red, activeTrackColor = Red, inactiveTrackColor = Panel2), modifier = Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatTime(position), color = Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                Text(formatTime(duration), color = Muted, fontSize = 11.sp)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AssistChip(onClick = { showSpeedMenu = true }, label = { Text("Speed ${speed}×") }, leadingIcon = { Icon(Icons.Default.Settings, null, modifier = Modifier.size(16.dp)) }, colors = AssistChipDefaults.assistChipColors(labelColor = White, leadingIconContentColor = White, containerColor = Panel2))
                AssistChip(onClick = onSubtitle, label = { Text(if (entry.subtitleUri != null) "Subtitle added" else "Add subtitle") }, leadingIcon = { Icon(Icons.Default.Subtitles, null, modifier = Modifier.size(16.dp)) }, colors = AssistChipDefaults.assistChipColors(labelColor = White, leadingIconContentColor = White, containerColor = Panel2))
                AssistChip(onClick = { showPlaylist = !showPlaylist }, label = { Text("Queue ${playlist.size}") }, leadingIcon = { Icon(Icons.Default.VideoLibrary, null, modifier = Modifier.size(16.dp)) }, colors = AssistChipDefaults.assistChipColors(labelColor = White, leadingIconContentColor = White, containerColor = Panel2))
            }
        }
        AnimatedVisibility(showPlaylist) {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 220.dp).background(Panel).padding(horizontal = 14.dp)) {
                items(playlist, key = { it.uri.toString() }) { item ->
                    Row(Modifier.fillMaxWidth().clickable { onNext(item); showPlaylist = false }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PlayArrow, null, tint = if (item.uri == entry.uri) Red else Muted)
                        Spacer(Modifier.width(8.dp))
                        Text(item.title, color = if (item.uri == entry.uri) White else Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.US, "%d:%02d", total / 60, total % 60)
}
