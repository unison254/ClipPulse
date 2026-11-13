package com.example.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class YouTubeUrlParserTest {

    private val service = YouTubeMetadataService()

    @Test
    fun extractVideoId_supportsStandardAndShortUrls() {
        assertEquals("dQw4w9WgXcQ", service.extractVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", service.extractVideoId("https://youtu.be/dQw4w9WgXcQ?si=abc123"))
        assertEquals("dQw4w9WgXcQ", service.extractVideoId("https://youtube.com/shorts/dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", service.extractVideoId("https://m.youtube.com/watch?v=dQw4w9WgXcQ&t=42s"))
        assertEquals("dQw4w9WgXcQ", service.extractVideoId("https://www.youtube.com/live/dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", service.extractVideoId("dQw4w9WgXcQ"))
    }

    @Test
    fun extractVideoId_rejectsBlankOrInvalidInputs() {
        assertNull(service.extractVideoId(""))
        assertNull(service.extractVideoId("https://vimeo.com/123456"))
        assertNull(service.extractVideoId("not a url"))
    }
}
