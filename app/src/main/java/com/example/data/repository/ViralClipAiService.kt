package com.example.data.repository

import android.util.Log
import com.example.model.CaptionCue
import com.example.model.GeneratedClipDraft
import com.example.model.GeneratedProjectPlan
import com.example.model.YouTubeMetadata
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.UUID

class ViralClipAiService {

    private val clipGenerationSchema = Schema.obj(
        properties = mapOf(
            "videoSummary" to Schema.string(
                description = "A concise 1-2 sentence summary of the video's viral potential and core theme."
            ),
            "clips" to Schema.array(
                description = "3 to 4 viral short-form clip candidates extracted from the video.",
                items = Schema.obj(
                    properties = mapOf(
                        "title" to Schema.string(description = "Punchy short-form clip title under 60 chars."),
                        "hookLine" to Schema.string(description = "Attention-grabbing 3-second opening hook overlay."),
                        "startSeconds" to Schema.integer(description = "Start timestamp in seconds within the source video."),
                        "endSeconds" to Schema.integer(description = "End timestamp in seconds (15 to 58 seconds after startSeconds)."),
                        "viralScore" to Schema.integer(description = "Viral potential score from 70 to 99."),
                        "viralReason" to Schema.string(description = "Why this moment retains viewers on Shorts/Reels/TikTok."),
                        "hashtags" to Schema.array(
                            items = Schema.string(),
                            description = "3 to 5 relevant short-form hashtags starting with #."
                        ),
                        "captions" to Schema.array(
                            description = "Sequential timed caption cues spanning from 0.0 up to clip duration.",
                            items = Schema.obj(
                                properties = mapOf(
                                    "startOffsetSec" to Schema.double(description = "Cue start time in seconds relative to clip start."),
                                    "endOffsetSec" to Schema.double(description = "Cue end time in seconds relative to clip start."),
                                    "text" to Schema.string(description = "Short 3-7 word punchy caption line."),
                                    "highlightWord" to Schema.string(description = "Single high-impact emphasis word from text.")
                                )
                            )
                        )
                    )
                )
            )
        )
    )

    private val captionRewriteSchema = Schema.obj(
        properties = mapOf(
            "captions" to Schema.array(
                description = "Rewritten timed caption cues matching the requested tone.",
                items = Schema.obj(
                    properties = mapOf(
                        "startOffsetSec" to Schema.double(description = "Cue start time in seconds relative to clip start."),
                        "endOffsetSec" to Schema.double(description = "Cue end time in seconds relative to clip start."),
                        "text" to Schema.string(description = "Short punchy caption line."),
                        "highlightWord" to Schema.string(description = "Single emphasis word from text.")
                    )
                )
            )
        )
    )

    private val clipPlannerModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = clipGenerationSchema
            }
        )
    }

    private val captionEditorModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = captionRewriteSchema
            }
        )
    }

    suspend fun generateViralClips(
        metadata: YouTubeMetadata,
        userContextOrTranscript: String,
        targetPlatform: String
    ): GeneratedProjectPlan {
        val transcriptSection = when {
            userContextOrTranscript.isNotBlank() && metadata.fetchedTranscript.isNotBlank() ->
                "User Focus/Notes:\n${userContextOrTranscript.take(2500)}\n\nFetched Video Transcript:\n${metadata.fetchedTranscript.take(4500)}"
            userContextOrTranscript.isNotBlank() ->
                "User Context/Transcript:\n${userContextOrTranscript.take(5000)}"
            metadata.fetchedTranscript.isNotBlank() ->
                "Fetched Video Transcript:\n${metadata.fetchedTranscript.take(6000)}"
            else ->
                "No transcript was embedded on this video. Infer realistic, engaging short-form highlight segments and natural spoken caption beats tailored closely to the video's title and creator topic."
        }

        val prompt = """
            You are an expert short-form viral video editor for $targetPlatform.
            Analyze this YouTube video and propose 3 to 4 high-retention vertical clip segments with dynamic timed captions.

            Video URL: ${metadata.canonicalUrl}
            Video Title: ${metadata.title}
            Channel: ${metadata.channelName}

            $transcriptSection

            Rules:
            - Each clip duration (endSeconds - startSeconds) must be between 15 and 55 seconds.
            - Ensure startSeconds >= 0 and endSeconds > startSeconds. If transcript timestamps like [02:14] are provided, align startSeconds and endSeconds closely to those moments!
            - For each clip, generate 5 to 10 crisp timed caption cues (3 to 7 words each) starting at 0.0s up to the clip duration, with sequential startOffsetSec < endOffsetSec.
            - Choose a single punchy highlightWord that actually appears inside that caption's text.
        """.trimIndent()

        try {
            val response = withTimeout(60_000L) {
                clipPlannerModel.generateContent(prompt)
            }
            val jsonText = response.text
                ?: throw IllegalStateException("Gemini returned an empty response.")
            return parseProjectPlan(jsonText)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate viral clips", e)
            throw e
        }
    }

    suspend fun rewriteCaptions(
        clipTitle: String,
        hookLine: String,
        currentCaptions: List<CaptionCue>,
        toneInstruction: String
    ): List<CaptionCue> {
        val serializedCues = currentCaptions.joinToString("\n") { cue ->
            "[${cue.startOffsetSec}s - ${cue.endOffsetSec}s] ${cue.text}"
        }
        val prompt = """
            Rewrite the following short-form video captions for the clip "$clipTitle" (Hook: "$hookLine").
            Goal / Style Instruction: $toneInstruction

            Current timed captions:
            $serializedCues

            Preserve the startOffsetSec and endOffsetSec timing rhythm closely while making every line punchy, readable, and engaging (3-7 words per cue), and pick one highlightWord from each cue's text.
        """.trimIndent()

        try {
            val response = withTimeout(60_000L) {
                captionEditorModel.generateContent(prompt)
            }
            val jsonText = response.text
                ?: throw IllegalStateException("Gemini returned an empty caption rewrite.")
            val root = JSONObject(jsonText)
            val arr = root.getJSONArray("captions")
            return parseCaptionsArray(arr)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to rewrite captions", e)
            throw e
        }
    }

    private fun parseProjectPlan(jsonText: String): GeneratedProjectPlan {
        val root = JSONObject(jsonText)
        val summary = root.optString("videoSummary").ifBlank {
            "AI-curated short-form highlights ready for vertical captions."
        }
        val clipsArray = root.getJSONArray("clips")
        val drafts = mutableListOf<GeneratedClipDraft>()

        for (i in 0 until clipsArray.length()) {
            val obj = clipsArray.getJSONObject(i)
            val start = obj.optLong("startSeconds", (i * 45L) + 10L).coerceAtLeast(0L)
            val rawEnd = obj.optLong("endSeconds", start + 30L)
            val end = if (rawEnd <= start) start + 25L else rawEnd

            val hashtagsArr = obj.optJSONArray("hashtags")
            val hashtags = buildList {
                if (hashtagsArr != null) {
                    for (h in 0 until hashtagsArr.length()) {
                        val tag = hashtagsArr.optString(h).trim()
                        if (tag.isNotBlank()) {
                            add(if (tag.startsWith("#")) tag else "#$tag")
                        }
                    }
                }
            }.ifEmpty { listOf("#shorts", "#viral", "#clippulse") }

            val captionsArr = obj.optJSONArray("captions")
            val captions = if (captionsArr != null) {
                parseCaptionsArray(captionsArr)
            } else {
                emptyList()
            }

            drafts.add(
                GeneratedClipDraft(
                    title = obj.optString("title").ifBlank { "Viral Clip ${i + 1}" },
                    hookLine = obj.optString("hookLine").ifBlank { "WATCH THIS MOMENT" },
                    startSeconds = start,
                    endSeconds = end,
                    viralScore = obj.optLong("viralScore", 88L).coerceIn(0L, 100L),
                    viralReason = obj.optString("viralReason").ifBlank { "Strong hook and pacing." },
                    hashtags = hashtags,
                    captions = captions
                )
            )
        }
        return GeneratedProjectPlan(videoSummary = summary, clips = drafts)
    }

    private fun parseCaptionsArray(arr: org.json.JSONArray): List<CaptionCue> {
        val list = mutableListOf<CaptionCue>()
        var lastEnd = 0.0
        for (j in 0 until arr.length()) {
            val item = arr.optJSONObject(j) ?: continue
            val start = item.optDouble("startOffsetSec", lastEnd).coerceAtLeast(0.0)
            val rawEnd = item.optDouble("endOffsetSec", start + 2.2)
            val end = if (rawEnd <= start) start + 1.8 else rawEnd
            lastEnd = end
            val text = item.optString("text").trim()
            if (text.isNotBlank()) {
                list.add(
                    CaptionCue(
                        id = "cue_${j}_${UUID.randomUUID().toString().take(6)}",
                        startOffsetSec = start,
                        endOffsetSec = end,
                        text = text,
                        highlightWord = item.optString("highlightWord").trim()
                    )
                )
            }
        }
        return list
    }

    private companion object {
        const val TAG = "ViralClipAiService"
    }
}
