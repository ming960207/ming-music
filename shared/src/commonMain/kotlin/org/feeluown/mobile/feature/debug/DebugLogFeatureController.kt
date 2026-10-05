package org.feeluown.mobile

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private val DEFAULT_DEBUG_LOG_FILTERS =
    setOf(DebugLogLevel.Info, DebugLogLevel.Warning, DebugLogLevel.Error)

data class DebugLogUiState(
    val lines: List<String> = emptyList(),
    val levelFilters: Set<DebugLogLevel> = DEFAULT_DEBUG_LOG_FILTERS,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val feedback: String? = null,
)

/** Feature-owned debug-log state and actions used by the production log viewer. */
interface DebugLogFeatureController {
    val isAvailable: Boolean
    val uiState: StateFlow<DebugLogUiState>

    fun refresh()
    fun onLevelFilterChange(level: DebugLogLevel, selected: Boolean)
    fun export(lines: List<String>)
    fun dismissFeedback(feedback: String)
}

fun createDebugLogFeatureController(
    repository: DebugLogRepository,
    scope: CoroutineScope,
): DebugLogFeatureController = DefaultDebugLogFeatureController(repository, scope)

private class DefaultDebugLogFeatureController(
    private val repository: DebugLogRepository,
    private val scope: CoroutineScope,
) : DebugLogFeatureController {
    private val mutableUiState = MutableStateFlow(DebugLogUiState())
    override val uiState: StateFlow<DebugLogUiState> = mutableUiState.asStateFlow()
    override val isAvailable: Boolean
        get() = repository.isAvailable

    override fun refresh() {
        if (!repository.isAvailable || mutableUiState.value.isLoading) return
        scope.launch {
            mutableUiState.value = mutableUiState.value.copy(
                isLoading = true,
                errorMessage = null,
            )
            try {
                val lines = repository.logLines()
                mutableUiState.value = mutableUiState.value.copy(
                    lines = lines,
                    isLoading = false,
                    errorMessage = null,
                )
            } catch (cancelled: CancellationException) {
                mutableUiState.value = mutableUiState.value.copy(isLoading = false)
                throw cancelled
            } catch (throwable: Throwable) {
                mutableUiState.value = mutableUiState.value.copy(
                    isLoading = false,
                    errorMessage = throwable.message ?: throwable::class.simpleName.orEmpty(),
                )
            }
        }
    }

    override fun onLevelFilterChange(level: DebugLogLevel, selected: Boolean) {
        val current = mutableUiState.value
        val filters = if (selected) {
            current.levelFilters + level
        } else {
            current.levelFilters - level
        }
        mutableUiState.value = current.copy(levelFilters = filters)
    }

    override fun export(lines: List<String>) {
        if (!repository.isAvailable || lines.isEmpty() || mutableUiState.value.isLoading) return
        scope.launch {
            mutableUiState.value = mutableUiState.value.copy(
                isLoading = true,
                errorMessage = null,
            )
            try {
                val feedback = repository.exportLogFile(lines)
                mutableUiState.value = mutableUiState.value.copy(
                    isLoading = false,
                    feedback = feedback,
                )
            } catch (cancelled: CancellationException) {
                mutableUiState.value = mutableUiState.value.copy(isLoading = false)
                throw cancelled
            } catch (throwable: Throwable) {
                mutableUiState.value = mutableUiState.value.copy(
                    isLoading = false,
                    errorMessage = throwable.message ?: throwable::class.simpleName.orEmpty(),
                )
            }
        }
    }

    override fun dismissFeedback(feedback: String) {
        val current = mutableUiState.value
        if (current.feedback == feedback) {
            mutableUiState.value = current.copy(feedback = null)
        }
    }
}
