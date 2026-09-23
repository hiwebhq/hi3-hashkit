package hi3.hashkit.integrations.plug

import hi3.hashkit.data.repo.MinerRepository
import hi3.hashkit.domain.model.MinerStatus
import hi3.hashkit.domain.model.MinerTelemetry
import hi3.hashkit.domain.model.Sourced
import hi3.hashkit.domain.model.ValueSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class PlugMeterTest {

    /** Verbatim `emeter.get_realtime` reply from a Kasa KP115 (2026-09-22). */
    private val kp115 = """{"emeter":{"get_realtime":{"current_ma":764,"voltage_mv":118791,"power_mw":90640,"total_wh":7076,"err_code":0}}}"""

    @Test
    fun `KP115 realtime maps mW and Wh`() {
        val r = SmartPlugClient.parseKasaRealtime(kp115)!!
        assertEquals(90.64, r.powerW!!, 1e-9)
        assertEquals(7076.0, r.energyTotalWh!!, 1e-9)
        assertNull(r.energyTodayWh)
    }

    @Test
    fun `older Kasa firmware reports W and kWh`() {
        val r = SmartPlugClient.parseKasaRealtime("""{"emeter":{"get_realtime":{"current":0.76,"voltage":118.8,"power":90.6,"total":7.076,"err_code":0}}}""")!!
        assertEquals(90.6, r.powerW!!, 1e-9)
        assertEquals(7076.0, r.energyTotalWh!!, 1e-6)
    }

    @Test
    fun `Kasa error replies are ignored`() {
        assertNull(SmartPlugClient.parseKasaRealtime("""{"emeter":{"err_code":-1,"err_msg":"module not support"}}"""))
    }

    @Test
    fun `KLAP energy usage maps mW and today Wh`() {
        val body = """{"error_code":0,"result":{"today_runtime":611,"month_runtime":18321,"today_energy":882,"month_energy":26130,"local_time":"2026-09-22 10:11:12","current_power":86230}}"""
        val r = SmartPlugClient.parseKasaEnergyUsage(body)!!
        assertEquals(86.23, r.powerW!!, 1e-9)
        assertEquals(882.0, r.energyTodayWh!!, 1e-9)
        assertNull(r.energyTotalWh)
    }

    @Test
    fun `Tasmota and Shelly counters normalize to Wh`() {
        val tas = SmartPlugClient.parseTasmotaStatus8("""{"StatusSNS":{"ENERGY":{"Total":12.345,"Today":0.5,"Power":91,"Voltage":119}}}""")!!
        assertEquals(91.0, tas.powerW!!, 1e-9)
        assertEquals(500.0, tas.energyTodayWh!!, 1e-9)
        assertEquals(12345.0, tas.energyTotalWh!!, 1e-9)
        val gen2 = SmartPlugClient.parseShellyGen2("""{"id":0,"output":true,"apower":88.4,"voltage":120.1,"aenergy":{"total":5432.1,"minute_ts":1}}""")!!
        assertEquals(88.4, gen2.powerW!!, 1e-9)
        assertEquals(5432.1, gen2.energyTotalWh!!, 1e-9)
        val gen1 = SmartPlugClient.parseShellyGen1("""{"power":80.0,"is_valid":true,"total":6000}""")!!
        assertEquals(100.0, gen1.energyTotalWh!!, 1e-9)
    }

    @Test
    fun `wall watts become effective power and the board figure is kept`() {
        val board = MinerTelemetry(
            timestamp = Instant.EPOCH, status = MinerStatus.ONLINE,
            hashrateGhs = Sourced.reported(1000.0), powerW = Sourced.reported(15.0),
            efficiencyJTh = Sourced.calculated(15.0),
        )
        val t = MinerRepository.applyPlugReading(board, PlugReading(powerW = 20.0, energyTodayWh = 100.0, energyTotalWh = 7076.0))
        assertEquals(20.0, t.powerW.value!!, 1e-9)
        assertEquals(ValueSource.MEASURED, t.powerW.source)
        assertEquals(20.0, t.wallPowerW.value!!, 1e-9)
        assertEquals(15.0, t.boardPowerW.value!!, 1e-9)
        assertEquals(ValueSource.REPORTED, t.boardPowerW.source)
        assertEquals(20.0, t.efficiencyJTh.value!!, 1e-9)
        assertEquals(100.0, t.plugEnergyTodayWh!!, 1e-9)
        assertEquals(7076.0, t.plugEnergyTotalWh!!, 1e-9)
    }

    @Test
    fun `a plug that only reports energy leaves power untouched`() {
        val board = MinerTelemetry(timestamp = Instant.EPOCH, status = MinerStatus.ONLINE, powerW = Sourced.reported(15.0))
        val t = MinerRepository.applyPlugReading(board, PlugReading(powerW = null, energyTotalWh = 5.0))
        assertEquals(15.0, t.powerW.value!!, 1e-9)
        assertNull(t.wallPowerW.value)
        assertEquals(5.0, t.plugEnergyTotalWh!!, 1e-9)
    }
}
