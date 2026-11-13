package com.example.ui.studio

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.ClipRepository
import com.example.model.ClipProject
import com.example.model.ViralClip
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ClipsUiState {
    data object Loading : ClipsUiState
    data class Success(val clips: List<ViralClip>) : ClipsUiState
    data class Error(val message: String) : ClipsUiState
}

class ProjectClipsViewModel(
    private val repository: ClipRepository,
    private val currentUserId: String,
    private val projectId: String
) : ViewModel() {

    private val _project = MutableStateFlow<ClipProject?>(null)
    val project: StateFlow<ClipProject?> = _project.asStateFlow()

    val clipsState: StateFlow<ClipsUiState> = repository.observeClips(currentUserId, projectId)
        .map<List<ViralClip>, ClipsUiState> { ClipsUiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing clips", rootError)
            emit(ClipsUiState.Error(rootError.message ?: "Failed to load viral clips."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = ClipsUiState.Loading
        )

    init {
        viewModelScope.launch {
            try {
                _project.value = repository.getProject(currentUserId, projectId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load project metadata", e)
            }
        }
    }

    private companion object {
        const val TAG = "ProjectClipsVM"
    }
}
