package com.example.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.ClipRepository
import com.example.data.repository.ViralClipAiService
import com.example.data.repository.YouTubeMetadataService
import com.example.model.ClipProject
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ProjectsUiState {
    data object Loading : ProjectsUiState
    data class Success(val projects: List<ClipProject>) : ProjectsUiState
    data class Error(val message: String) : ProjectsUiState
}

sealed interface GenerationStatus {
    data object Idle : GenerationStatus
    data object FetchingVideo : GenerationStatus
    data class AnalyzingClips(val videoTitle: String) : GenerationStatus
    data class Success(val projectId: String) : GenerationStatus
    data class Error(val message: String) : GenerationStatus
}

data class HomeGeneratorFormState(
    val urlInput: String = "",
    val parsedVideoId: String? = null,
    val notesInput: String = "",
    val selectedPlatform: String = "YouTube Shorts",
    val showNotesField: Boolean = false,
    val generationStatus: GenerationStatus = GenerationStatus.Idle
)

class HomeViewModel(
    private val repository: ClipRepository,
    private val youtubeService: YouTubeMetadataService,
    private val aiService: ViralClipAiService,
    private val currentUserId: String
) : ViewModel() {

    val projectsState: StateFlow<ProjectsUiState> = repository.observeProjects(currentUserId)
        .map<List<ClipProject>, ProjectsUiState> { ProjectsUiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing projects", rootError)
            emit(ProjectsUiState.Error(rootError.message ?: "Failed to load your projects."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = ProjectsUiState.Loading
        )

    private val _formState = MutableStateFlow(HomeGeneratorFormState())
    val formState: StateFlow<HomeGeneratorFormState> = _formState.asStateFlow()

    fun onUrlChanged(newUrl: String) {
        val videoId = youtubeService.extractVideoId(newUrl)
        _formState.update {
            it.copy(
                urlInput = newUrl,
                parsedVideoId = videoId,
                generationStatus = if (it.generationStatus is GenerationStatus.Error) {
                    GenerationStatus.Idle
                } else {
                    it.generationStatus
                }
            )
        }
    }

    fun onNotesChanged(newNotes: String) {
        _formState.update { it.copy(notesInput = newNotes) }
    }

    fun onPlatformSelected(platform: String) {
        _formState.update { it.copy(selectedPlatform = platform) }
    }

    fun toggleNotesField() {
        _formState.update { it.copy(showNotesField = !it.showNotesField) }
    }

    fun consumeGeneratedNavigation() {
        _formState.update {
            if (it.generationStatus is GenerationStatus.Success) {
                it.copy(
                    urlInput = "",
                    parsedVideoId = null,
                    notesInput = "",
                    generationStatus = GenerationStatus.Idle
                )
            } else {
                it
            }
        }
    }

    fun generateViralClips() {
        val current = _formState.value
        val rawUrl = current.urlInput.trim()
        if (rawUrl.isEmpty()) {
            _formState.update {
                it.copy(generationStatus = GenerationStatus.Error("Paste a YouTube video or Shorts URL first."))
            }
            return
        }

        viewModelScope.launch {
            try {
                _formState.update { it.copy(generationStatus = GenerationStatus.FetchingVideo) }
                val metadata = youtubeService.fetchMetadata(rawUrl)

                _formState.update {
                    it.copy(generationStatus = GenerationStatus.AnalyzingClips(metadata.title))
                }
                val plan = aiService.generateViralClips(
                    metadata = metadata,
                    userContextOrTranscript = current.notesInput.trim(),
                    targetPlatform = current.selectedPlatform
                )

                val projectId = repository.createProjectWithClips(
                    youtubeUrl = metadata.canonicalUrl,
                    videoId = metadata.videoId,
                    videoTitle = metadata.title,
                    channelName = metadata.channelName,
                    thumbnailUrl = metadata.thumbnailUrl,
                    videoSummary = plan.videoSummary,
                    clips = plan.clips
                )

                _formState.update {
                    it.copy(generationStatus = GenerationStatus.Success(projectId))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Clip generation failed", e)
                _formState.update {
                    it.copy(
                        generationStatus = GenerationStatus.Error(
                            e.localizedMessage ?: "Could not generate viral clips. Please check your URL and try again."
                        )
                    )
                }
            }
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            try {
                repository.deleteProject(projectId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete project $projectId", e)
            }
        }
    }

    private companion object {
        const val TAG = "HomeViewModel"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
