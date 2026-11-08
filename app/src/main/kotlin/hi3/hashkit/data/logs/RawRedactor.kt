package hi3.hashkit.data.logs

/**
 * Redacts credential-bearing fields in raw firmware JSON (`/api/system/info` and friends)
 * **before it is stored** in the raw-response table, so a plaintext Wi-Fi password, stratum
 * password or token from a fork/old firmware never rests on disk — not just before display.
 * Pure; unit-tested.
 */
object RawRedactor {

    private val field = Regex(
        "\"([^\"]*(?:User|user|pass|Pass|ssid|SSID|Token|token|secret|Secret|psk|PSK)[^\"]*)\"\\s*:\\s*\"[^\"]*\"",
    )

    fun redact(body: String?): String? {
        if (body == null) return null
        return field.replace(body) { m -> "\"${m.groupValues[1]}\": \"[redacted]\"" }
    }
}
