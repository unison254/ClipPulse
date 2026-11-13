package com.example.data.repository

import android.net.Uri
import android.util.Log
import com.example.model.YouTubeMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class YouTubeMetadataService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun extractVideoId(rawInput: String): String? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return null

        // Allow pasting an 11-char ID directly
        if (trimmed.matches(Regex("^[a-zA-Z0-9_-]{11}$"))) {
            return trimmed
        }

        val normalizedUrl = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }

        try {
            val uri = Uri.parse(normalizedUrl)
            val host = uri.host?.lowercase() ?: ""
            if (host.contains("youtu.be")) {
                val seg = uri.pathSegments.firstOrNull()
                if (!seg.isNullOrBlank() && seg.matches(Regex("^[a-zA-Z0-9_-]{11}$"))) {
                    return seg
                }
            }
            if (host.contains("youtube.com") || host.contains("youtube-nocookie.com")) {
                val vParam = uri.getQueryParameter("v")
                if (!vParam.isNullOrBlank() && vParam.matches(Regex("^[a-zA-Z0-9_-]{11}$"))) {
                    return vParam
                }
                val segments = uri.pathSegments
                if (segments.size >= 2 && segments[0] in listOf("shorts", "embed", "live", "v")) {
                    val candidate = segments[1]
                    if (candidate.matches(Regex("^[a-zA-Z0-9_-]{11}$"))) {
                        return candidate
                    }
                }
            }
        } catch (_: Exception) {
            // Fall through to regex fallback
        }

        val regex = "(?:youtube\\.com/(?:watch\\?.*v=|shorts/|embed/|live/)|youtu\\.be/)([a-zA-Z0-9_-]{11})"
        val matcher = Pattern.compile(regex).matcher(trimmed)
        return if (matcher.find()) matcher.group(1) else null
    }

    suspend fun fetchMetadata(rawUrl: String): YouTubeMetadata = withContext(Dispatchers.IO) {
        val videoId = extractVideoId(rawUrl)
            ?: throw IllegalArgumentException("Please enter a valid YouTube video or Shorts URL.")

        val canonicalUrl = "https://www.youtube.com/watch?v=$videoId"
        val encodedUrl = URLEncoder.encode(canonicalUrl, "UTF-8")
        val oEmbedUrl = "https://www.youtube.com/oembed?url=$encodedUrl&format=json"

        val request = Request.Builder()
            .url(oEmbedUrl)
            .header("User-Agent", "Mozilla/5.0 (Android; ClipPulse)")
            .build()

        val oEmbedJson = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException(
                    "Could not load YouTube video (HTTP ${response.code}). Check that the video is public and the URL is valid."
                )
            }
            val body = response.body?.string()
                ?: throw IllegalStateException("Empty metadata response from YouTube.")
            JSONObject(body)
        }

        val title = oEmbedJson.optString("title").ifBlank { "YouTube Video ($videoId)" }
        val channelName = oEmbedJson.optString("author_name").ifBlank { "YouTube Creator" }
        val thumbnailUrl = oEmbedJson.optString("thumbnail_url")
            .ifBlank { "https://i.ytimg.com/vi/$videoId/hqdefault.jpg" }

        val transcript = try {
            fetchCaptionTranscriptBestEffort(canonicalUrl)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "Optional transcript fetch skipped: ${e.message}")
            ""
        }

        YouTubeMetadata(
            videoId = videoId,
            canonicalUrl = canonicalUrl,
            title = title,
            channelName = channelName,
            thumbnailUrl = thumbnailUrl,
            fetchedTranscript = transcript
        )
    }

    private fun fetchCaptionTranscriptBestEffort(watchUrl: String): String {
        val pageRequest = Request.Builder()
            .url(watchUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()

        val html = client.newCall(pageRequest).execute().use { resp ->
            if (!resp.isSuccessful) return ""
            resp.body?.string().orEmpty()
        }

        val marker = "\"captionTracks\":"
        val idx = html.indexOf(marker)
        if (idx == -1) return ""

        val arrayStart = html.indexOf('[', idx)
        if (arrayStart == -1) return ""
        val arrayEnd = html.indexOf(']', arrayStart)
        if (arrayEnd == -1) return ""

        val tracksJson = html.substring(arrayStart, arrayEnd + 1)
        val urlRegex = Regex("\"baseUrl\"\\s*:\\s*\"([^\"]+)\"")
        val match = urlRegex.find(tracksJson) ?: return ""
        val rawBaseUrl = match.groupValues[1]
            .replace("\\u0026", "&")
            .replace("\\/", "/")

        val captionUrl = if (rawBaseUrl.contains("fmt=")) rawBaseUrl else "$rawBaseUrl&fmt=json3"
        val captionRequest = Request.Builder()
            .url(captionUrl)
            .header("User-Agent", "Mozilla/5.0")
            .build()

        val json3Body = client.newCall(captionRequest).execute().use { resp ->
            if (!resp.isSuccessful) return ""
            resp.body?.string().orEmpty()
        }
        if (!json3Body.trimStart().startsWith("{")) return ""

        val root = JSONObject(json3Body)
        val events = root.optJSONArray("events") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until events.length()) {
            val ev = events.optJSONObject(i) ?: continue
            val segs = ev.optJSONArray("segs") ?: continue
            val startMs = ev.optLong("tStartMs", 0L)
            val sec = startMs / 1000L
            val lineText = buildString {
                for (j in 0 until segs.length()) {
                    append(segs.optJSONObject(j)?.optString("utf8").orEmpty())
                }
            }.replace("\n", " ").trim()
            if (lineText.isNotBlank()) {
                val mins = sec / 60
                val remSec = sec % 60
                sb.append("[%02d:%02d] %s\n".format(mins, remSec, lineText))
                if (sb.length >= 6000) break
            }
        }
        return sb.toString().trim()
    }

    private companion object {
        const val TAG = "YouTubeMetadataService"
    }
}
