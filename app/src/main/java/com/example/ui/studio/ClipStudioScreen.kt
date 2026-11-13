package com.example.ui.studio

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.BuildConfig
import com.example.R
import com.example.model.CaptionCue
import com.example.model.CaptionPosition
import com.example.model.CaptionStyle
import com.example.ui.theme.ElectricCoral
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.NeonLime
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioOutline
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioSurfaceHighlight
import com.example.ui.theme.SuccessGreen
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipStudioScreen(
    viewModel: ClipStudioViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var isPlaying by remember { mutableStateOf(false) }
    var useYouTubeWebPlayer by remember { mutableStateOf(true) }
    var currentOffsetSec by remember { mutableDoubleStateOf(0.0) }
    var seekSignal by remember { mutableIntStateOf(0) }
    var seekTargetSec by remember { mutableDoubleStateOf(0.0) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var cueBeingEdited by remember { mutableStateOf<CaptionCue?>(null) }

    val clipDuration = state.durationSeconds.toDouble()

    LaunchedEffect(isPlaying, clipDuration) {
        while (isPlaying) {
            delay(100L)
            val next = currentOffsetSec + 0.1
            currentOffsetSec = if (next >= clipDuration) 0.0 else next
        }
    }

    val activeCue = remember(state.captions, currentOffsetSec) {
        state.captions.firstOrNull { cue ->
            currentOffsetSec >= cue.startOffsetSec && currentOffsetSec <= cue.endOffsetSec
        } ?: state.captions.firstOrNull()
    }

    if (cueBeingEdited != null) {
        EditCueDialog(
            cue = cueBeingEdited!!,
            onDismiss = { cueBeingEdited = null },
            onSave = { text, highlight, start, end ->
                viewModel.updateCue(cueBeingEdited!!.id, text, highlight, start, end)
                cueBeingEdited = null
            }
        )
    }

    Scaffold(
        containerColor = StudioObsidian,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = state.title.ifBlank { "Clip Studio" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (state.hasUnsavedChanges) {
                            Text(
                                text = "Unsaved edits",
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonLime
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("studio_back_button")) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = viewModel::saveChanges,
                        enabled = !state.isSaving && !state.isLoading,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (state.hasUnsavedChanges) ElectricCoral else StudioSurfaceHighlight,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .padding(end = 10.dp)
                            .testTag("save_clip_button")
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Save,
                                contentDescription = stringResource(R.string.cd_save_clip),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = StudioObsidian,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = ElectricCoral)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .widthIn(max = 840.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (state.errorMessage != null) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = state.errorMessage.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            if (state.statusBanner != null) {
                item {
                    Surface(
                        color = SuccessGreen.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = SuccessGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = state.statusBanner.orEmpty(),
                                color = SuccessGreen,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }

            // 9:16 Vertical Preview Stage
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "9:16 Vertical Preview",
                            style = MaterialTheme.typography.labelLarge,
                            color = ElectricCyan
                        )
                        FilterChip(
                            selected = useYouTubeWebPlayer,
                            onClick = { useYouTubeWebPlayer = !useYouTubeWebPlayer },
                            label = {
                                Text(if (useYouTubeWebPlayer) "YouTube Embed" else "Poster Frame")
                            }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .widthIn(max = 260.dp)
                            .fillMaxWidth(0.72f)
                            .aspectRatio(9f / 16f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.Black)
                    ) {
                        val videoId = state.project?.videoId.orEmpty()
                        if (useYouTubeWebPlayer && videoId.isNotBlank()) {
                            YouTubeClipWebView(
                                videoId = videoId,
                                startSeconds = state.startSeconds,
                                endSeconds = state.endSeconds,
                                isPlaying = isPlaying,
                                seekSignal = seekSignal,
                                seekTargetOffsetSec = seekTargetSec,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            AsyncImage(
                                model = state.project?.thumbnailUrl,
                                contentDescription = state.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Subtle framing gradient for caption legibility
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.Black.copy(alpha = 0.25f),
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.55f)
                                        )
                                    )
                                )
                        )

                        // Live Styled Captions & Hook Overlay
                        LiveCaptionOverlay(
                            activeCue = activeCue,
                            hookLine = state.hookLine,
                            currentOffsetSec = currentOffsetSec,
                            style = state.captionStyle,
                            position = state.captionPosition,
                            scale = state.captionScale,
                            uppercase = state.uppercaseCaptions
                        )
                    }

                    // Playback & Scrubber Bar
                    Surface(
                        color = StudioSurface,
                        border = BorderStroke(1.dp, StudioOutline),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { isPlaying = !isPlaying },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(ElectricCoral)
                                    .testTag("play_pause_preview_button")
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = stringResource(R.string.cd_play_pause),
                                    tint = Color.White
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Slider(
                                    value = currentOffsetSec.toFloat().coerceIn(0f, clipDuration.toFloat()),
                                    onValueChange = { newVal ->
                                        currentOffsetSec = newVal.toDouble()
                                        seekTargetSec = newVal.toDouble()
                                        seekSignal++
                                    },
                                    valueRange = 0f..clipDuration.toFloat().coerceAtLeast(1f),
                                    colors = SliderDefaults.colors(
                                        thumbColor = ElectricCyan,
                                        activeTrackColor = ElectricCoral
                                    )
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = String.format(Locale.US, "%.1fs", currentOffsetSec),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ElectricCyan
                                    )
                                    Text(
                                        text = "${formatTimestamp(state.startSeconds)} → ${formatTimestamp(state.endSeconds)} (${state.durationSeconds}s)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Studio Editor Tabs
            item {
                val tabs = listOf("Caption Style", "Captions (${state.captions.size})", "Trim & Export")
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = StudioSurfaceElevated,
                    contentColor = ElectricCoral,
                    modifier = Modifier.clip(RoundedCornerShape(14.dp))
                ) {
                    tabs.forEachIndexed { index, label ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    text = label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        )
                    }
                }
            }

            when (selectedTab) {
                0 -> {
                    item {
                        CaptionStyleTabContent(
                            state = state,
                            onSelectStyle = viewModel::selectCaptionStyle,
                            onSelectPosition = viewModel::selectCaptionPosition,
                            onScaleChange = viewModel::updateCaptionScale,
                            onUppercaseToggle = viewModel::toggleUppercase,
                            onHookLineChange = viewModel::updateHookLine
                        )
                    }
                }

                1 -> {
                    item {
                        CaptionToolsHeader(
                            isRewriting = state.isRewritingCaptions,
                            onRewrite = viewModel::rewriteCaptionsWithAi,
                            onAddCue = viewModel::addCaptionCue
                        )
                    }
                    items(state.captions, key = { it.id }) { cue ->
                        CaptionCueRowCard(
                            cue = cue,
                            isActive = activeCue?.id == cue.id,
                            onSeek = {
                                currentOffsetSec = cue.startOffsetSec
                                seekTargetSec = cue.startOffsetSec
                                seekSignal++
                            },
                            onEdit = { cueBeingEdited = cue },
                            onDelete = { viewModel.deleteCaptionCue(cue.id) }
                        )
                    }
                }

                else -> {
                    item {
                        TrimAndExportTabContent(
                            state = state,
                            onTitleChange = viewModel::updateTitle,
                            onAdjustStart = viewModel::adjustStartSeconds,
                            onAdjustEnd = viewModel::adjustEndSeconds,
                            onHashtagsChange = viewModel::updateHashtagsText,
                            onExportSrt = {
                                exportSrtViaShareSheet(
                                    context = context,
                                    clipId = state.clipId,
                                    srtContent = viewModel.buildSrtContent()
                                )
                            },
                            onSharePostCopy = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, state.title)
                                    putExtra(Intent.EXTRA_TEXT, viewModel.buildSharePostCopy())
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Viral Clip Copy"))
                            }
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun CaptionStyleTabContent(
    state: ClipEditorState,
    onSelectStyle: (CaptionStyle) -> Unit,
    onSelectPosition: (CaptionPosition) -> Unit,
    onScaleChange: (Double) -> Unit,
    onUppercaseToggle: (Boolean) -> Unit,
    onHookLineChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = "Viral Caption Presets",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(CaptionStyle.entries) { preset ->
                val selected = state.captionStyle == preset
                Surface(
                    color = if (selected) ElectricCoral.copy(alpha = 0.2f) else StudioSurfaceElevated,
                    border = BorderStroke(
                        width = if (selected) 1.5.dp else 1.dp,
                        color = if (selected) ElectricCoral else StudioOutline
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onSelectStyle(preset) }
                        .testTag("preset_${preset.name}")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = preset.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (selected) ElectricCoral else Color.White
                        )
                        Text(
                            text = preset.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Surface(
            color = StudioSurfaceElevated,
            border = BorderStroke(1.dp, StudioOutline),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Vertical Placement", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CaptionPosition.entries.forEach { pos ->
                        FilterChip(
                            selected = state.captionPosition == pos,
                            onClick = { onSelectPosition(pos) },
                            label = { Text(pos.displayName) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricCyan.copy(alpha = 0.2f),
                                selectedLabelColor = ElectricCyan
                            )
                        )
                    }
                }

                Text(
                    text = "Caption Size (${String.format(Locale.US, "%.2fx", state.captionScale)})",
                    style = MaterialTheme.typography.labelLarge
                )
                Slider(
                    value = state.captionScale.toFloat(),
                    onValueChange = { onScaleChange(it.toDouble()) },
                    valueRange = 0.7f..1.5f,
                    colors = SliderDefaults.colors(
                        thumbColor = NeonLime,
                        activeTrackColor = ElectricCoral
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("ALL CAPS Captions", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "High-contrast short-form emphasis",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.uppercaseCaptions,
                        onCheckedChange = onUppercaseToggle
                    )
                }

                OutlinedTextField(
                    value = state.hookLine,
                    onValueChange = onHookLineChange,
                    label = { Text("Opening 3s Hook Banner") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun CaptionToolsHeader(
    isRewriting: Boolean,
    onRewrite: (String) -> Unit,
    onAddCue: () -> Unit
) {
    Surface(
        color = StudioSurfaceElevated,
        border = BorderStroke(1.dp, StudioOutline),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Text("AI Caption Rewrite", style = MaterialTheme.typography.titleMedium)
                }
                OutlinedButton(
                    onClick = onAddCue,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Cue")
                }
            }

            if (isRewriting) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = ElectricCyan, strokeWidth = 2.dp)
                    Text("Rewriting captions with Gemini...", style = MaterialTheme.typography.bodyMedium, color = ElectricCyan)
                }
            } else {
                val tones = listOf(
                    "Punchier viral hooks",
                    "Ultra-short 3-word bursts",
                    "Add engaging emojis",
                    "Translate to Spanish"
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(tones) { tone ->
                        FilterChip(
                            selected = false,
                            onClick = { onRewrite(tone) },
                            label = { Text(tone) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CaptionCueRowCard(
    cue: CaptionCue,
    isActive: Boolean,
    onSeek: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = if (isActive) ElectricCoral.copy(alpha = 0.14f) else StudioSurface,
        border = BorderStroke(
            width = 1.dp,
            color = if (isActive) ElectricCoral else StudioOutline
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onSeek)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = String.format(Locale.US, "%.1fs – %.1fs", cue.startOffsetSec, cue.endOffsetSec),
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricCyan
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = cue.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (cue.highlightWord.isNotBlank()) {
                    Text(
                        text = "Highlight: ${cue.highlightWord}",
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonLime
                    )
                }
            }

            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = "Edit caption cue",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Delete caption cue",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun TrimAndExportTabContent(
    state: ClipEditorState,
    onTitleChange: (String) -> Unit,
    onAdjustStart: (Long) -> Unit,
    onAdjustEnd: (Long) -> Unit,
    onHashtagsChange: (String) -> Unit,
    onExportSrt: () -> Unit,
    onSharePostCopy: () -> Unit
) {
    Surface(
        color = StudioSurfaceElevated,
        border = BorderStroke(1.dp, StudioOutline),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedTextField(
                value = state.title,
                onValueChange = onTitleChange,
                label = { Text("Clip Title") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Text("Trim YouTube Segment", style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Start: ${formatTimestamp(state.startSeconds)}", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { onAdjustStart(-5L) }) { Text("-5s") }
                    OutlinedButton(onClick = { onAdjustStart(-1L) }) { Text("-1s") }
                    OutlinedButton(onClick = { onAdjustStart(1L) }) { Text("+1s") }
                    OutlinedButton(onClick = { onAdjustStart(5L) }) { Text("+5s") }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("End: ${formatTimestamp(state.endSeconds)}", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { onAdjustEnd(-5L) }) { Text("-5s") }
                    OutlinedButton(onClick = { onAdjustEnd(-1L) }) { Text("-1s") }
                    OutlinedButton(onClick = { onAdjustEnd(1L) }) { Text("+1s") }
                    OutlinedButton(onClick = { onAdjustEnd(5L) }) { Text("+5s") }
                }
            }

            OutlinedTextField(
                value = state.hashtagsText,
                onValueChange = onHashtagsChange,
                label = { Text("Hashtags") },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = onExportSrt,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricCyan,
                    contentColor = StudioObsidian
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("export_srt_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Subtitles,
                    contentDescription = stringResource(R.string.cd_export_srt)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Export .SRT Captions File",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }

            OutlinedButton(
                onClick = onSharePostCopy,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("share_post_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Share,
                    contentDescription = stringResource(R.string.cd_share_copy)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Share Viral Hook, Link & Captions")
            }
        }
    }
}

@Composable
private fun EditCueDialog(
    cue: CaptionCue,
    onDismiss: () -> Unit,
    onSave: (text: String, highlightWord: String, startSec: Double, endSec: Double) -> Unit
) {
    var text by remember(cue.id) { mutableStateOf(cue.text) }
    var highlight by remember(cue.id) { mutableStateOf(cue.highlightWord) }
    var startStr by remember(cue.id) { mutableStateOf(String.format(Locale.US, "%.1f", cue.startOffsetSec)) }
    var endStr by remember(cue.id) { mutableStateOf(String.format(Locale.US, "%.1f", cue.endOffsetSec)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Caption Cue") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Caption Text") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = highlight,
                    onValueChange = { highlight = it },
                    label = { Text("Highlight Word (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startStr,
                        onValueChange = { startStr = it },
                        label = { Text("Start (s)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = endStr,
                        onValueChange = { endStr = it },
                        label = { Text("End (s)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val start = startStr.toDoubleOrNull() ?: cue.startOffsetSec
                    val end = endStr.toDoubleOrNull() ?: cue.endOffsetSec
                    onSave(text.trim(), highlight.trim(), start, end)
                }
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun exportSrtViaShareSheet(
    context: Context,
    clipId: String,
    srtContent: String
) {
    val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
    val srtFile = File(exportDir, "clippulse_${clipId.take(8)}.srt")
    srtFile.writeText(srtContent)

    val uri = FileProvider.getUriForFile(
        context,
        "${BuildConfig.APPLICATION_ID}.fileprovider",
        srtFile
    )

    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "application/x-subrip"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "ClipPulse Captions (.srt)")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(shareIntent, "Export .SRT Captions"))
}
