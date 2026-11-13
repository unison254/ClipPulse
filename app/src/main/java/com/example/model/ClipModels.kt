package com.example.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class CaptionStyle(val displayName: String, val subtitle: String) {
    HORMOZI_POP("Hormozi Pop", "Bold punch + neon highlight"),
    CLEAN_GLASS("Clean Glass", "Frosted pill + crisp sans"),
    NEON_PULSE("Neon Pulse", "Electric cyan glow"),
    EDITORIAL("Editorial", "Minimal high-contrast")
}

enum class CaptionPosition(val displayName: String) {
    TOP("Top"),
    CENTER("Center"),
    BOTTOM("Bottom")
}

@IgnoreExtraProperties
data class CaptionCue(
    val id: String = "",
    val startOffsetSec: Double = 0.0,
    val endOffsetSec: Double = 2.5,
    val text: String = "",
    val highlightWord: String = ""
) {
    fun toFirestoreMap(): Map<String, Any> = mapOf(
        "id" to id,
        "startOffsetSec" to startOffsetSec,
        "endOffsetSec" to endOffsetSec,
        "text" to text,
        "highlightWord" to highlightWord
    )
}

@IgnoreExtraProperties
data class ClipProject(
    @DocumentId val id: String = "",
    val userId: String = "",
    val youtubeUrl: String = "",
    val videoId: String = "",
    val videoTitle: String = "",
    val channelName: String = "",
    val thumbnailUrl: String = "",
    val videoSummary: String = "",
    val clipCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ViralClip(
    @DocumentId val id: String = "",
    val userId: String = "",
    val projectId: String = "",
    val title: String = "",
    val hookLine: String = "",
    val startSeconds: Long = 0L,
    val endSeconds: Long = 30L,
    val viralScore: Long = 85L,
    val viralReason: String = "",
    val captionStyle: String = CaptionStyle.HORMOZI_POP.name,
    val captionPosition: String = CaptionPosition.BOTTOM.name,
    val captionScale: Double = 1.0,
    val uppercaseCaptions: Boolean = true,
    val captions: List<CaptionCue> = emptyList(),
    val hashtags: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun styleEnum(): CaptionStyle =
        CaptionStyle.entries.firstOrNull { it.name == captionStyle } ?: CaptionStyle.HORMOZI_POP

    fun positionEnum(): CaptionPosition =
        CaptionPosition.entries.firstOrNull { it.name == captionPosition } ?: CaptionPosition.BOTTOM

    val durationSeconds: Long
        get() = (endSeconds - startSeconds).coerceAtLeast(1L)
}

data class YouTubeMetadata(
    val videoId: String,
    val canonicalUrl: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String,
    val fetchedTranscript: String = ""
)

data class GeneratedClipDraft(
    val title: String,
    val hookLine: String,
    val startSeconds: Long,
    val endSeconds: Long,
    val viralScore: Long,
    val viralReason: String,
    val hashtags: List<String>,
    val captions: List<CaptionCue>
)

data class GeneratedProjectPlan(
    val videoSummary: String,
    val clips: List<GeneratedClipDraft>
)
