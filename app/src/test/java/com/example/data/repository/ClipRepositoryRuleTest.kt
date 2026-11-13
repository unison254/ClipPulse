package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.model.CaptionCue
import com.example.model.CaptionPosition
import com.example.model.CaptionStyle
import com.example.model.GeneratedClipDraft
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createProjectWithClips_authenticatedOwner_persistsAndObservesData() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repository = ClipRepository(firestore)

        val draft = GeneratedClipDraft(
            title = "Hook Mastery",
            hookLine = "NEVER START WITH HEY GUYS",
            startSeconds = 15L,
            endSeconds = 48L,
            viralScore = 92L,
            viralReason = "High retention opener",
            hashtags = listOf("#shorts", "#hooks"),
            captions = listOf(
                CaptionCue(
                    id = "cue_1",
                    startOffsetSec = 0.0,
                    endOffsetSec = 2.5,
                    text = "Stop saying hey guys in your intros",
                    highlightWord = "Stop"
                )
            )
        )

        val projectId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createProjectWithClips(
                youtubeUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                videoId = "dQw4w9WgXcQ",
                videoTitle = "Retention Secrets",
                channelName = "Creator Lab",
                thumbnailUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
                videoSummary = "Great short-form takeaways",
                clips = listOf(draft)
            )
        }
        assertTrue(projectId.isNotBlank())

        val projects = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeProjects(aliceUid).first { list -> list.any { it.id == projectId } }
        }
        assertTrue(projects.any { it.id == projectId && it.videoTitle == "Retention Secrets" })

        val clips = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeClips(aliceUid, projectId).first { it.isNotEmpty() }
        }
        assertEquals(1, clips.size)
        val savedClip = clips.first()
        assertEquals("Hook Mastery", savedClip.title)
        assertEquals(1, savedClip.captions.size)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.updateClip(
                projectId = projectId,
                clipId = savedClip.id,
                title = "Hook Mastery v2",
                hookLine = "FIX YOUR FIRST 3 SECONDS",
                startSeconds = 10L,
                endSeconds = 45L,
                captionStyle = CaptionStyle.NEON_PULSE,
                captionPosition = CaptionPosition.CENTER,
                captionScale = 1.2,
                uppercaseCaptions = false,
                captions = savedClip.captions,
                hashtags = listOf("#viral", "#editing")
            )
        }

        val updatedClip = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.getClip(aliceUid, projectId, savedClip.id)
        }
        assertNotNull(updatedClip)
        assertEquals("Hook Mastery v2", updatedClip?.title)
        assertEquals(CaptionStyle.NEON_PULSE.name, updatedClip?.captionStyle)
    }

    @Test
    fun getProject_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = ClipRepository(firestore)
        val projectId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createProjectWithClips(
                youtubeUrl = "https://youtu.be/abc12345678",
                videoId = "abc12345678",
                videoTitle = "Alice Video",
                channelName = "Alice",
                thumbnailUrl = "https://i.ytimg.com/vi/abc12345678/hqdefault.jpg",
                videoSummary = "Summary",
                clips = emptyList()
            )
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = ClipRepository(firestore)
        val error = runCatching {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                bobRepo.getProject(aliceUid, projectId)
            }
        }.exceptionOrNull()

        val firestoreEx = error?.unwrapFirestoreException()
        assertNotNull("Expected FirebaseFirestoreException, got $error", firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    @Test
    fun observeProjects_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repository = ClipRepository(firestore)

        val error = runCatching {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeProjects("some_user_id").first()
            }
        }.exceptionOrNull()

        val firestoreEx = error?.unwrapFirestoreException()
        assertNotNull("Expected FirebaseFirestoreException, got $error", firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice@clippulse.test"
        const val BOB_EMAIL = "bob@clippulse.test"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
