package hi3.hashkit.adapters.espminer

import org.junit.Assert.assertEquals
import org.junit.Test

class LogRedactorTest {

    @Test
    fun `strips ansi color codes`() {
        assertEquals(
            "I (259531412) fan_controller: Temp: 66.8°C, SetPoint: 63.0°C",
            LogRedactor.clean("[0;32mI (259531412) fan_controller: Temp: 66.8°C, SetPoint: 63.0°C[0m"),
        )
    }

    @Test
    fun `redacts bech32 and base58 wallets keeping the worker suffix`() {
        assertEquals(
            "stratum_task: login [wallet].0x203 ok",
            LogRedactor.clean("stratum_task: login bc1qexampleredactedwalletaddr0e8mgy4vk3dh.0x203 ok"),
        )
        assertEquals(
            "user [wallet].worker1 authorized",
            LogRedactor.clean("user 3ExampRedactedBase58AddrXYZ23abcd.worker1 authorized"),
        )
    }

    @Test
    fun `ordinary lines pass through untouched`() {
        val line = "I (785486741) power_management: Temperature 0: 64.94 C"
        assertEquals(line, LogRedactor.clean(line))
        // Short hex/ids must not be mistaken for wallets.
        assertEquals(
            "asic_result: Nonce difficulty 1122.33 of 5188",
            LogRedactor.clean("asic_result: Nonce difficulty 1122.33 of 5188"),
        )
    }
}
