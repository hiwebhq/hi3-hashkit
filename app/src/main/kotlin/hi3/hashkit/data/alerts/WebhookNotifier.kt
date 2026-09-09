package hi3.hashkit.data.alerts

import hi3.hashkit.data.prefs.SettingsRepository
import hi3.hashkit.domain.alerts.AlertSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/** Where alert notifications are mirrored so they reach you when the app is closed. */
enum class WebhookType(val label: String) {
    NONE("Off"),
    NTFY("ntfy"),
    GOTIFY("Gotify"),
    TELEGRAM("Telegram"),
    GENERIC("Generic (JSON POST)");

    companion object {
        fun fromName(name: String?): WebhookType = entries.firstOrNull { it.name == name } ?: NONE
    }
}

/**
 * Mirrors alert signals to a user-configured push service so notifications arrive even when
 * the app is closed — no cloud account of ours involved. This is a deliberate outbound
 * request to the endpoint the user configures (ntfy topic, Gotify server, Telegram bot, or
 * any URL), so the LAN-only rule does not apply here. Fails silently; the local notification
 * still fires regardless.
 */
@Singleton
class WebhookNotifier @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val settingsRepository: SettingsRepository,
) {
    private val textMedia = "text/plain".toMediaType()
    private val jsonMedia = "application/json".toMediaType()

    suspend fun send(signal: AlertSignal) {
        val s = settingsRepository.current()
        val type = s.webhookType
        if (type == WebhookType.NONE) return
        // Only push active alerts by default — recoveries stay in-app to avoid noise.
        if (!signal.active) return
        val title = "Hi3 Hashkit: ${signal.minerName}"
        val text = signal.message
        withContext(Dispatchers.IO) {
            runCatching {
                val req = when (type) {
                    WebhookType.NTFY -> {
                        val url = s.webhookUrl.trim().ifEmpty { return@runCatching }
                        Request.Builder().url(url)
                            .addHeader("Title", title)
                            .addHeader("Tags", if (signal.active) "warning" else "white_check_mark")
                            .post(text.toRequestBody(textMedia)).build()
                    }
                    WebhookType.GOTIFY -> {
                        val base = s.webhookUrl.trim().removeSuffix("/")
                        val token = s.webhookToken.trim()
                        val url = "$base/message?token=$token".toHttpUrlOrNull() ?: return@runCatching
                        val body = """{"title":${jsonStr(title)},"message":${jsonStr(text)},"priority":5}"""
                        Request.Builder().url(url).post(body.toRequestBody(jsonMedia)).build()
                    }
                    WebhookType.TELEGRAM -> {
                        val token = s.webhookToken.trim()
                        val chat = s.webhookTarget.trim()
                        if (token.isEmpty() || chat.isEmpty()) return@runCatching
                        val body = """{"chat_id":${jsonStr(chat)},"text":${jsonStr("$title\n$text")}}"""
                        Request.Builder().url("https://api.telegram.org/bot$token/sendMessage")
                            .post(body.toRequestBody(jsonMedia)).build()
                    }
                    WebhookType.GENERIC -> {
                        val url = s.webhookUrl.trim().ifEmpty { return@runCatching }
                        val body = """{"title":${jsonStr(title)},"message":${jsonStr(text)},""" +
                            """"miner":${jsonStr(signal.minerName)},"type":${jsonStr(signal.type.name)},""" +
                            """"active":${signal.active}}"""
                        Request.Builder().url(url).post(body.toRequestBody(jsonMedia)).build()
                    }
                    WebhookType.NONE -> return@runCatching
                }
                okHttpClient.newCall(req).execute().use { /* fire-and-forget */ }
            }
        }
    }

    private fun jsonStr(s: String): String =
        "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
}
