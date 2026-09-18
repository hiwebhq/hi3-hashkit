package hi3.hashkit.adapters.futurebit

import hi3.hashkit.discovery.MinerHostValidator
import hi3.hashkit.domain.adapter.MinerHost
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Transport for the Apollo OS dashboard API (FutureBit Apollo II / Apollo BTC running
 * Apollo OS 2.x): a GraphQL endpoint at `POST http://<host>:5000/api/graphql`.
 *
 * Verified live against an Apollo II on 2026-09-18:
 *  - every data root (`Miner`, `Mcu`, `Node`, `Pool`, `Settings`) answers
 *    `"You have to login first"` (error type `authentication`) without a token;
 *  - `Auth.status` and schema introspection are open, which is what the probe uses;
 *  - `Auth.login(input:{password})` returns `accessToken`, sent back as a Bearer header.
 *
 * Tokens are cached per host and refreshed once, transparently, when the device answers
 * an authentication error (token expiry or a password change on the miner).
 */
@Singleton
class ApolloGraphQlClient @Inject constructor(
    private val client: OkHttpClient,
) {
    sealed interface Response {
        /** HTTP 2xx with a JSON body; [data] is the GraphQL `data` object (may be empty). */
        data class Ok(val body: String, val data: JsonObject) : Response
        data class HttpError(val code: Int) : Response
        data class NetworkError(val cause: String) : Response
    }

    sealed interface Authed {
        data class Ok(val body: String, val data: JsonObject) : Authed
        /** No credential saved for this miner, so nothing beyond `Auth.status` is readable. */
        data object NeedsPassword : Authed
        /** The device rejected the saved password. */
        data class AuthFailed(val message: String) : Authed
        data class HttpError(val code: Int) : Authed
        data class NetworkError(val cause: String) : Authed
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val tokens = ConcurrentHashMap<String, String>()

    /** One unauthenticated (or explicitly tokened) request. */
    fun query(host: MinerHost, query: String, variables: JsonObject? = null, token: String? = null): Response {
        if (!MinerHostValidator.resolvesToAllowed(host.host)) {
            return Response.NetworkError("Refused: ${host.host} is not a private/Tailscale address")
        }
        val payload = buildJsonObject {
            put("query", query)
            if (variables != null) put("variables", variables)
        }.toString()
        val builder = Request.Builder()
            .url("http://${host.host}:${host.port}$PATH")
            .post(payload.toRequestBody(JSON_TYPE))
        if (token != null) builder.header("Authorization", "Bearer $token")
        return try {
            client.newCall(builder.build()).execute().use { resp ->
                if (!resp.isSuccessful) return Response.HttpError(resp.code)
                val body = resp.body?.string().orEmpty()
                val data = runCatching { json.parseToJsonElement(body).jsonObject["data"] as? JsonObject }
                    .getOrNull() ?: JsonObject(emptyMap())
                Response.Ok(body, data)
            }
        } catch (e: IOException) {
            Response.NetworkError(e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * Run [query] with the miner's session token, logging in with [MinerHost.secret] when
     * no token is cached and re-logging in once if the device reports the token as invalid.
     */
    fun authedQuery(host: MinerHost, query: String, variables: JsonObject? = null): Authed {
        val password = host.secret?.takeIf { it.isNotBlank() } ?: return Authed.NeedsPassword
        val key = keyOf(host)
        // Try the cached token first; a stale one (device rebooted, password changed) shows
        // up as an authentication error and falls through to a fresh login below.
        val cached = tokens[key]?.let { toAuthed(query(host, query, variables, it)) }
        if (cached != null && cached !is Authed.AuthFailed) return cached
        tokens.remove(key)
        return when (val login = login(host, password)) {
            is LoginResult.Ok -> toAuthed(query(host, query, variables, login.token))
            is LoginResult.Rejected -> Authed.AuthFailed(login.message)
            is LoginResult.HttpError -> Authed.HttpError(login.code)
            is LoginResult.NetworkError -> Authed.NetworkError(login.cause)
        }
    }

    private fun toAuthed(response: Response): Authed = when (response) {
        is Response.Ok -> {
            val authError = authErrorMessage(response.data)
            if (authError != null) Authed.AuthFailed(authError) else Authed.Ok(response.body, response.data)
        }
        is Response.HttpError -> Authed.HttpError(response.code)
        is Response.NetworkError -> Authed.NetworkError(response.cause)
    }

    private fun keyOf(host: MinerHost) = "${host.host}:${host.port}"

    /** Drop the cached token for a host (e.g. after the user changes the saved password). */
    fun forget(host: MinerHost) {
        tokens.remove(keyOf(host))
    }

    private sealed interface LoginResult {
        data class Ok(val token: String) : LoginResult
        data class Rejected(val message: String) : LoginResult
        data class HttpError(val code: Int) : LoginResult
        data class NetworkError(val cause: String) : LoginResult
    }

    private fun login(host: MinerHost, password: String): LoginResult {
        val vars = buildJsonObject { put("password", password) }
        return when (val r = query(host, LOGIN_QUERY, vars)) {
            is Response.Ok -> {
                val login = r.data.obj("Auth")?.obj("login")
                val token = login?.obj("result")?.str("accessToken")
                if (!token.isNullOrBlank()) {
                    tokens[keyOf(host)] = token
                    LoginResult.Ok(token)
                } else {
                    LoginResult.Rejected(login?.obj("error")?.str("message") ?: "Login rejected")
                }
            }
            is Response.HttpError -> LoginResult.HttpError(r.code)
            is Response.NetworkError -> LoginResult.NetworkError(r.cause)
        }
    }

    companion object {
        const val DEFAULT_PORT = 5000
        const val PATH = "/api/graphql"
        private val JSON_TYPE = "application/json".toMediaType()

        /** Apollo OS exposes login as a field on the query root, not a mutation. */
        const val LOGIN_QUERY =
            "query Login(\$password: String!) { Auth { login(input: { password: \$password }) " +
                "{ result { accessToken } error { type message } } } }"

        /** True when any `error` object in the response is an authentication error. */
        fun hasAuthError(data: JsonElement): Boolean = authErrorMessage(data) != null

        /** Message of the first authentication `error` in the response, if any. */
        fun authErrorMessage(data: JsonElement): String? {
            when (data) {
                is JsonObject -> {
                    val err = data["error"] as? JsonObject
                    if (err != null) {
                        val type = err.str("type")
                        val message = err.str("message") ?: ""
                        if (type == "authentication" || message.contains("login", ignoreCase = true)) {
                            return message.ifBlank { "Not authenticated" }
                        }
                    }
                    for (v in data.values) authErrorMessage(v)?.let { return it }
                }
                is JsonArray -> for (v in data) authErrorMessage(v)?.let { return it }
                else -> Unit
            }
            return null
        }
    }
}
