package dev.solsynth.cloudysky.notifications

import android.content.Context
import dev.solsynth.cloudysky.R
import dev.solsynth.cloudysky.auth.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NotificationRepository(
    context: Context,
    private val authRepository: AuthRepository,
) {
    private val appContext = context.applicationContext
    private val api = NotificationApi()

    suspend fun getNotifications(offset: Int = 0, take: Int = 20): Result<List<NotificationItem>> {
        return try {
            val accessToken = authRepository.accessToken()
                ?: return Result.failure(Exception(appContext.getString(R.string.no_access_token)))

            val items = withContext(Dispatchers.IO) {
                api.getNotifications(accessToken = accessToken, take = take, offset = offset)
            } ?: return Result.failure(Exception(appContext.getString(R.string.failed_to_load_notifications)))

            Result.success(items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
