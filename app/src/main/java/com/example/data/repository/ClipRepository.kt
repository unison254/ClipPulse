package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.firebase.OperationType
import com.example.data.firebase.handleFirestoreError
import com.example.model.CaptionCue
import com.example.model.CaptionPosition
import com.example.model.CaptionStyle
import com.example.model.ClipProject
import com.example.model.GeneratedClipDraft
import com.example.model.ViralClip
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ClipRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeProjects(userId: String): Flow<List<ClipProject>> = flow {
        val path = "users/$userId/projects"
        emitAll(
            db.collection("users").document(userId).collection("projects")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ClipProject::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeClips(userId: String, projectId: String): Flow<List<ViralClip>> = flow {
        val path = "users/$userId/projects/$projectId/clips"
        emitAll(
            db.collection("users").document(userId)
                .collection("projects").document(projectId)
                .collection("clips")
                .orderBy("viralScore", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ViralClip::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun getProject(userId: String, projectId: String): ClipProject? {
        val docRef = db.collection("users").document(userId)
            .collection("projects").document(projectId)
        return try {
            docRef.get().await().toObject(
                ClipProject::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    suspend fun getClip(userId: String, projectId: String, clipId: String): ViralClip? {
        val docRef = db.collection("users").document(userId)
            .collection("projects").document(projectId)
            .collection("clips").document(clipId)
        return try {
            docRef.get().await().toObject(
                ViralClip::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    suspend fun createProjectWithClips(
        youtubeUrl: String,
        videoId: String,
        videoTitle: String,
        channelName: String,
        thumbnailUrl: String,
        videoSummary: String,
        clips: List<GeneratedClipDraft>
    ): String {
        val uid = requireUserId()
        val projectRef = db.collection("users").document(uid)
            .collection("projects").document()
        val projectId = projectRef.id

        val safeClipCount = clips.size.coerceAtMost(50).toLong()
        val projectPayload = mapOf(
            "userId" to uid,
            "youtubeUrl" to youtubeUrl.take(500),
            "videoId" to videoId.take(64),
            "videoTitle" to videoTitle.ifBlank { "YouTube Video" }.take(300),
            "channelName" to channelName.ifBlank { "Creator" }.take(200),
            "thumbnailUrl" to thumbnailUrl.take(500),
            "videoSummary" to videoSummary.take(2000),
            "clipCount" to safeClipCount,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        try {
            projectRef.set(projectPayload).await()

            if (clips.isNotEmpty()) {
                val batch = db.batch()
                clips.take(50).forEach { draft ->
                    val clipRef = projectRef.collection("clips").document()
                    val safeStart = draft.startSeconds.coerceIn(0L, 86390L)
                    val safeEnd = draft.endSeconds.coerceIn(safeStart + 1L, 86400L)
                    val clipPayload = mapOf(
                        "userId" to uid,
                        "projectId" to projectId,
                        "title" to draft.title.ifBlank { "Viral Highlight" }.take(200),
                        "hookLine" to draft.hookLine.take(300),
                        "startSeconds" to safeStart,
                        "endSeconds" to safeEnd,
                        "viralScore" to draft.viralScore.coerceIn(0L, 100L),
                        "viralReason" to draft.viralReason.take(1000),
                        "captionStyle" to CaptionStyle.HORMOZI_POP.name,
                        "captionPosition" to CaptionPosition.BOTTOM.name,
                        "captionScale" to 1.0,
                        "uppercaseCaptions" to true,
                        "captions" to draft.captions.take(100).map { it.toFirestoreMap() },
                        "hashtags" to draft.hashtags.take(15).map { it.take(50) },
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    batch.set(clipRef, clipPayload)
                }
                batch.commit().await()
            }
            return projectId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, projectRef.path)
            throw e
        }
    }

    suspend fun updateClip(
        projectId: String,
        clipId: String,
        title: String,
        hookLine: String,
        startSeconds: Long,
        endSeconds: Long,
        captionStyle: CaptionStyle,
        captionPosition: CaptionPosition,
        captionScale: Double,
        uppercaseCaptions: Boolean,
        captions: List<CaptionCue>,
        hashtags: List<String>
    ) {
        val uid = requireUserId()
        require(projectId.isNotBlank()) { "Project ID must not be blank" }
        require(clipId.isNotBlank()) { "Clip ID must not be blank" }

        val docRef = db.collection("users").document(uid)
            .collection("projects").document(projectId)
            .collection("clips").document(clipId)

        val safeStart = startSeconds.coerceIn(0L, 86390L)
        val safeEnd = endSeconds.coerceIn(safeStart + 1L, 86400L)

        val updateMap = mapOf(
            "title" to title.ifBlank { "Untitled Clip" }.take(200),
            "hookLine" to hookLine.take(300),
            "startSeconds" to safeStart,
            "endSeconds" to safeEnd,
            "captionStyle" to captionStyle.name,
            "captionPosition" to captionPosition.name,
            "captionScale" to captionScale.coerceIn(0.6, 2.0),
            "uppercaseCaptions" to uppercaseCaptions,
            "captions" to captions.take(100).map { it.toFirestoreMap() },
            "hashtags" to hashtags.take(15).map { it.take(50) },
            "updatedAt" to FieldValue.serverTimestamp()
        )

        try {
            docRef.update(updateMap).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteProject(projectId: String) {
        val uid = requireUserId()
        require(projectId.isNotBlank()) { "Project ID must not be blank" }
        val projectRef = db.collection("users").document(uid)
            .collection("projects").document(projectId)
        try {
            val clipsSnapshot = projectRef.collection("clips").get().await()
            val batch = db.batch()
            clipsSnapshot.documents.forEach { clipDoc ->
                batch.delete(clipDoc.reference)
            }
            batch.delete(projectRef)
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, projectRef.path)
            throw e
        }
    }
}
