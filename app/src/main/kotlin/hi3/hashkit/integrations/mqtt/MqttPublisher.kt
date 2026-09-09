package hi3.hashkit.integrations.mqtt

import hi3.hashkit.data.prefs.SettingsRepository
import hi3.hashkit.domain.model.Miner
import hi3.hashkit.domain.model.MinerStatus
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Publishes fleet + per-miner telemetry to a local MQTT broker (e.g. the Mosquitto add-on
 * in Home Assistant) so miners can drive automations and dashboards. Opt-in and off by
 * default; best-effort (never disrupts polling). Optionally emits Home Assistant
 * MQTT-discovery config so entities appear automatically.
 *
 * What leaves the device: miner names, status, hashrate, power, temperature and efficiency
 * — to the broker the user configures. No addresses, credentials, or worker/pool data.
 */
@Singleton
class MqttPublisher @Inject constructor(
    private val client: MqttClient,
    private val settingsRepository: SettingsRepository,
) {
    /** Miner ids we've already sent HA discovery for this process, to avoid re-announcing. */
    private val announced = Collections.synchronizedSet(mutableSetOf<Long>())
    @Volatile private var fleetAnnounced = false

    suspend fun publish(miners: List<Miner>) {
        val s = settingsRepository.current()
        if (!s.mqttEnabled || s.mqttHost.isBlank()) return
        val real = miners.filter { !it.isDemo }
        val base = s.mqttBaseTopic
        val password = settingsRepository.mqttPassword()

        val messages = mutableListOf<MqttClient.Message>()
        messages += MqttClient.Message("$base/status", "online")

        // Fleet aggregate.
        val online = real.count { it.status == MinerStatus.ONLINE }
        val offline = real.count { it.status == MinerStatus.OFFLINE }
        val live = real.filter { it.status == MinerStatus.ONLINE || it.status == MinerStatus.DEGRADED }
        val totalHash = live.sumOf { it.lastTelemetry?.hashrateGhs?.value ?: 0.0 }
        val totalPower = live.sumOf { it.lastTelemetry?.powerW?.value ?: 0.0 }
        val worstTemp = live.mapNotNull { it.lastTelemetry?.chipTempC?.value }.maxOrNull()
        messages += MqttClient.Message(
            "$base/fleet/state",
            buildJsonObject {
                put("total_hashrate_ghs", round1(totalHash))
                put("total_power_w", round1(totalPower))
                put("online", online)
                put("offline", offline)
                put("total", real.size)
                if (worstTemp != null) put("worst_chip_temp_c", round1(worstTemp))
            }.toString(),
        )

        // Per-miner state.
        for (m in real) {
            val t = m.lastTelemetry
            messages += MqttClient.Message(
                "$base/miner/${m.id}/state",
                buildJsonObject {
                    put("name", m.name)
                    put("status", m.status.name.lowercase())
                    put("online", m.status == MinerStatus.ONLINE || m.status == MinerStatus.DEGRADED)
                    t?.hashrateGhs?.value?.let { put("hashrate_ghs", round1(it)) }
                    t?.powerW?.value?.let { put("power_w", round1(it)) }
                    t?.chipTempC?.value?.let { put("chip_temp_c", round1(it)) }
                    t?.efficiencyJTh?.value?.let { put("efficiency_jth", round1(it)) }
                }.toString(),
            )
        }

        // Home Assistant MQTT discovery (retained config), announced once per entity.
        if (s.mqttHomeAssistantDiscovery) {
            if (!fleetAnnounced) {
                messages += fleetDiscovery(base)
                fleetAnnounced = true
            }
            for (m in real) {
                if (announced.add(m.id)) messages += minerDiscovery(base, m)
            }
        }

        client.publish(
            host = s.mqttHost,
            port = s.mqttPort,
            clientId = "hi3hashkit-${base.hashCode().toUInt().toString(16)}",
            username = s.mqttUsername.ifBlank { null },
            password = password,
            messages = messages,
        )
    }

    /** Re-announce discovery on the next publish (call when the config changes). */
    fun resetDiscovery() {
        announced.clear()
        fleetAnnounced = false
    }

    private fun fleetDiscovery(base: String): List<MqttClient.Message> {
        val device = buildJsonObject {
            putJsonArray("identifiers") { add("hi3hashkit_fleet") }
            put("name", "Hi3 Hashkit fleet")
            put("manufacturer", "Hi3")
            put("model", "Hashkit")
        }
        fun sensor(key: String, name: String, unit: String?, tmpl: String) = MqttClient.Message(
            "homeassistant/sensor/hi3hashkit_fleet_$key/config",
            buildJsonObject {
                put("name", name)
                put("unique_id", "hi3hashkit_fleet_$key")
                put("state_topic", "$base/fleet/state")
                put("value_template", tmpl)
                if (unit != null) put("unit_of_measurement", unit)
                putJsonObject("device") { device.forEach { (k, v) -> put(k, v) } }
            }.toString(),
        )
        return listOf(
            sensor("hashrate", "Fleet hashrate", "GH/s", "{{ value_json.total_hashrate_ghs }}"),
            sensor("power", "Fleet power", "W", "{{ value_json.total_power_w }}"),
            sensor("online", "Miners online", null, "{{ value_json.online }}"),
        )
    }

    private fun minerDiscovery(base: String, m: Miner): List<MqttClient.Message> {
        val device = buildJsonObject {
            putJsonArray("identifiers") { add("hi3hashkit_miner_${m.id}") }
            put("name", m.name)
            put("manufacturer", m.identity.manufacturer ?: "")
            put("model", m.identity.model ?: m.adapterType)
        }
        val state = "$base/miner/${m.id}/state"
        fun sensor(key: String, name: String, unit: String?, tmpl: String) = MqttClient.Message(
            "homeassistant/sensor/hi3hashkit_miner_${m.id}_$key/config",
            buildJsonObject {
                put("name", "${m.name} $name")
                put("unique_id", "hi3hashkit_miner_${m.id}_$key")
                put("state_topic", state)
                put("value_template", tmpl)
                if (unit != null) put("unit_of_measurement", unit)
                putJsonObject("device") { device.forEach { (k, v) -> put(k, v) } }
            }.toString(),
        )
        return listOf(
            sensor("hashrate", "hashrate", "GH/s", "{{ value_json.hashrate_ghs }}"),
            sensor("power", "power", "W", "{{ value_json.power_w }}"),
            sensor("temp", "chip temp", "°C", "{{ value_json.chip_temp_c }}"),
            sensor("status", "status", null, "{{ value_json.status }}"),
        )
    }

    private fun round1(v: Double): Double = Math.round(v * 10.0) / 10.0
}
