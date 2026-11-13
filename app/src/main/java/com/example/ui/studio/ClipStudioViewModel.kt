package com.example.ui.studio

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.ClipRepository
import com.example.data.repository.ViralClipAiService
import com.example.model.CaptionCue
import com.example.model.CaptionPosition
import com.example.model.CaptionStyle
import com.example.model.ClipProject
import com.example.model.ViralClip
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class ClipEditorState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val project: ClipProject? = null,
    val clipId: String = "",
    val title: String = "",
    val hookLine: String = "",
    val startSeconds: Long = 0L,
    val endSeconds: Long = 30L,
    val viralScore: Long = 90L,
    val captionStyle: CaptionStyle = CaptionStyle.HORMOZI_POP,
    val captionPosition: CaptionPosition = CaptionPosition.BOTTOM,
    val captionScale: Double = 1.0,
    val uppercaseCaptions: Boolean = true,
    val captions: List<CaptionCue> = emptyList(),
    val hashtagsText: String = "",
    val hasUnsavedChanges: Boolean = false,
    val isSaving: Boolean = false,
    val isRewritingCaptions: Boolean = false,
    val statusBanner: String? = null
) {
    val durationSeconds: Long
        get() = (endSeconds - startSeconds).coerceAtLeast(1L)
}

class ClipStudioViewModel(
    private val repository: ClipRepository,
    private val aiService: ViralClipAiService,
    private val currentUserId: String,
    private val projectId: String,
    private val clipId: String
) : ViewModel() {

    private val _state = MutableStateFlow(ClipEditorState())
    val state: StateFlow<ClipEditorState> = _state.asStateFlow()

    init {
        loadClipAndProject()
    }

    private fun loadClipAndProject() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val proj = repository.getProject(currentUserId, projectId)
                val clip: ViralClip? = repository.getClip(currentUserId, projectId, clipId)
                if (clip == null) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = "Clip not found."
                        )
                    }
                    return@launch
                }
                _state.update {
                    it.copy(
                        isLoading = false,
                        project = proj,
                        clipId = clip.id,
                        title = clip.title,
                        hookLine = clip.hookLine,
                        startSeconds = clip.startSeconds,
                        endSeconds = clip.endSeconds,
                        viralScore = clip.viralScore,
                        captionStyle = clip.styleEnum(),
                        captionPosition = clip.positionEnum(),
                        captionScale = clip.captionScale,
                        uppercaseCaptions = clip.uppercaseCaptions,
                        captions = clip.captions,
                        hashtagsText = clip.hashtags.joinToString(" "),
                        hasUnsavedChanges = false
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load clip studio", e)
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.localizedMessage ?: "Failed to load clip."
                    )
                }
            }
        }
    }

    fun updateTitle(value: String) {
        _state.update { it.copy(title = value, hasUnsavedChanges = true, statusBanner = null) }
    }

    fun updateHookLine(value: String) {
        _state.update { it.copy(hookLine = value, hasUnsavedChanges = true, statusBanner = null) }
    }

    fun adjustStartSeconds(delta: Long) {
        _state.update { s ->
            val newStart = (s.startSeconds + delta).coerceIn(0L, (s.endSeconds - 3L).coerceAtLeast(0L))
            s.copy(startSeconds = newStart, hasUnsavedChanges = true, statusBanner = null)
        }
    }

    fun adjustEndSeconds(delta: Long) {
        _state.update { s ->
            val newEnd = (s.endSeconds + delta).coerceIn(s.startSeconds + 3L, 86400L)
            s.copy(endSeconds = newEnd, hasUnsavedChanges = true, statusBanner = null)
        }
    }

    fun selectCaptionStyle(style: CaptionStyle) {
        _state.update { it.copy(captionStyle = style, hasUnsavedChanges = true, statusBanner = null) }
    }

    fun selectCaptionPosition(position: CaptionPosition) {
        _state.update { it.copy(captionPosition = position, hasUnsavedChanges = true, statusBanner = null) }
    }

    fun updateCaptionScale(scale: Double) {
        _state.update { it.copy(captionScale = scale.coerceIn(0.7, 1.6), hasUnsavedChanges = true, statusBanner = null) }
    }

    fun toggleUppercase(enabled: Boolean) {
        _state.update { it.copy(uppercaseCaptions = enabled, hasUnsavedChanges = true, statusBanner = null) }
    }

    fun updateHashtagsText(value: String) {
        _state.update { it.copy(hashtagsText = value, hasUnsavedChanges = true, statusBanner = null) }
    }

    fun updateCue(
        cueId: String,
        newText: String,
        newHighlightWord: String,
        newStartSec: Double,
        newEndSec: Double
    ) {
        _state.update { s ->
            val updated = s.captions.map { cue ->
                if (cue.id == cueId) {
                    val safeStart = newStartSec.coerceAtLeast(0.0)
                    val safeEnd = newEndSec.coerceAtLeast(safeStart + 0.5)
                    cue.copy(
                        text = newText,
                        highlightWord = newHighlightWord,
                        startOffsetSec = safeStart,
                        endOffsetSec = safeEnd
                    )
                } else {
                    cue
                }
            }.sortedBy { it.startOffsetSec }
            s.copy(captions = updated, hasUnsavedChanges = true, statusBanner = null)
        }
    }

    fun addCaptionCue() {
        _state.update { s ->
            val lastEnd = s.captions.maxOfOrNull { it.endOffsetSec } ?: 0.0
            val newCue = CaptionCue(
                id = "cue_${UUID.randomUUID().toString().take(8)}",
                startOffsetSec = lastEnd,
                endOffsetSec = lastEnd + 2.5,
                text = "Add your viral caption line",
                highlightWord = "viral"
            )
            s.copy(
                captions = s.captions + newCue,
                hasUnsavedChanges = true,
                statusBanner = null
            )
        }
    }

    fun deleteCaptionCue(cueId: String) {
        _state.update { s ->
            s.copy(
                captions = s.captions.filterNot { it.id == cueId },
                hasUnsavedChanges = true,
                statusBanner = null
            )
        }
    }

    fun rewriteCaptionsWithAi(toneInstruction: String) {
        val current = _state.value
        if (current.isRewritingCaptions || current.captions.isEmpty()) return

        viewModelScope.launch {
            _state.update { it.copy(isRewritingCaptions = true, statusBanner = null, errorMessage = null) }
            try {
                val rewritten = aiService.rewriteCaptions(
                    clipTitle = current.title,
                    hookLine = current.hookLine,
                    currentCaptions = current.captions,
                    toneInstruction = toneInstruction
                )
                if (rewritten.isNotEmpty()) {
                    _state.update {
                        it.copy(
                            captions = rewritten,
                            isRewritingCaptions = false,
                            hasUnsavedChanges = true,
                            statusBanner = "Captions updated by AI ($toneInstruction)"
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            isRewritingCaptions = false,
                            statusBanner = "AI returned no captions to apply."
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI caption rewrite failed", e)
                _state.update {
                    it.copy(
                        isRewritingCaptions = false,
                        errorMessage = e.localizedMessage ?: "Caption rewrite failed."
                    )
                }
            }
        }
    }

    fun saveChanges() {
        val current = _state.value
        if (current.isSaving) return

        val hashtags = current.hashtagsText
            .split(Regex("[\\s,]+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { if (it.startsWith("#")) it else "#$it" }
            .take(15)

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, statusBanner = null, errorMessage = null) }
            try {
                repository.updateClip(
                    projectId = projectId,
                    clipId = clipId,
                    title = current.title,
                    hookLine = current.hookLine,
                    startSeconds = current.startSeconds,
                    endSeconds = current.endSeconds,
                    captionStyle = current.captionStyle,
                    captionPosition = current.captionPosition,
                    captionScale = current.captionScale,
                    uppercaseCaptions = current.uppercaseCaptions,
                    captions = current.captions,
                    hashtags = hashtags
                )
                _state.update {
                    it.copy(
                        isSaving = false,
                        hasUnsavedChanges = false,
                        statusBanner = "Saved to your cloud studio"
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save clip", e)
                _state.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = e.localizedMessage ?: "Failed to save changes."
                    )
                }
            }
        }
    }

    fun buildSrtContent(): String {
        val cues = _state.value.captions
        return buildString {
            cues.forEachIndexed { index, cue ->
                append(index + 1)
                append('\n')
                append(formatSrtTime(cue.startOffsetSec))
                append(" --> ")
                append(formatSrtTime(cue.endOffsetSec))
                append('\n')
                append(if (_state.value.uppercaseCaptions) cue.text.uppercase() else cue.text)
                append("\n\n")
            }
        }
    }

    fun buildSharePostCopy(): String {
        val s = _state.value
        val link = s.project?.youtubeUrl?.let { "$it&t=${s.startSeconds}s" }.orEmpty()
        return buildString {
            appendLine(s.title)
            if (s.hookLine.isNotBlank()) appendLine("Hook: ${s.hookLine}")
            if (link.isNotBlank()) appendLine("Clip Source (${formatTimestamp(s.startSeconds)}-${formatTimestamp(s.endSeconds)}): $link")
            appendLine()
            appendLine("Captions:")
            s.captions.forEach { appendLine("• ${it.text}") }
            if (s.hashtagsText.isNotBlank()) {
                appendLine()
                appendLine(s.hashtagsText)
            }
        }
    }

    private fun formatSrtTime(seconds: Double): String {
        val totalMs = (seconds * 1000.0).toLong().coerceAtLeast(0L)
        val hours = totalMs / 3_600_000L
        val mins = (totalMs % 3_600_000L) / 60_000L
        val secs = (totalMs % 60_000L) / 1000L
        val ms = totalMs % 1000L
        return "%02d:%02d:%02d,%03d".format(hours, mins, secs, ms)
    }

    private companion object {
        const val TAG = "ClipStudioVM"
    }
}
