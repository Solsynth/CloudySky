package dev.solsynth.cloudysky.notifications

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NotificationController(
    private val repository: NotificationRepository,
    private val scope: CoroutineScope,
) {
    private val pageSize = 20
    private val _uiState = MutableStateFlow(NotificationUiState())
    val uiState: StateFlow<NotificationUiState> = _uiState.asStateFlow()

    private var offset = 0
    private var loadJob: Job? = null

    fun refresh() {
        loadNotifications(reset = true)
    }

    fun loadMore() {
        if (_uiState.value.isLoading || _uiState.value.isLoadingMore || !_uiState.value.hasMore) return
        loadNotifications(reset = false)
    }

    fun clear() {
        loadJob?.cancel()
        offset = 0
        _uiState.value = NotificationUiState()
    }

    private fun loadNotifications(reset: Boolean) {
        loadJob?.cancel()
        loadJob = scope.launch {
            if (reset) {
                offset = 0
                _uiState.value = _uiState.value.copy(isLoading = true, error = null, hasMore = true)
            } else {
                _uiState.value = _uiState.value.copy(isLoadingMore = true, error = null)
            }

            val result = repository.getNotifications(offset = offset, take = pageSize)
            result.fold(
                onSuccess = { items ->
                    val notifications = if (reset) items else _uiState.value.notifications + items
                    offset = notifications.size
                    _uiState.value = _uiState.value.copy(
                        notifications = notifications,
                        isLoading = false,
                        isLoadingMore = false,
                        hasMore = items.size >= pageSize,
                    )
                },
                onFailure = { exception ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        error = exception.message ?: "Failed to load notifications",
                    )
                }
            )
        }
    }
}
