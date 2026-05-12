package dev.solsynth.cloudysky.notifications

import dev.solsynth.cloudysky.auth.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NotificationRepository(
    private val authRepository: AuthRepository,
) {
    private val api = NotificationApi()

    suspend fun getNotifications(offset: Int = 0, take: Int = 20): Result<List<NotificationItem>> {
        return try {
            val accessToken = authRepository.accessToken()
                ?: return Result.failure(Exception("No access token"))

            val items = withContext(Dispatchers.IO) {
                api.getNotifications(accessToken = accessToken, take = take, offset = offset)
            } ?: return Result.failure(Exception("Failed to load notifications"))

            Result.success(items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
