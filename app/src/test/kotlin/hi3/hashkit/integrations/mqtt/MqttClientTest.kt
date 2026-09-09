package hi3.hashkit.integrations.mqtt

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class MqttClientTest {

    @Test fun remainingLengthSingleByte() {
        assertArrayEquals(byteArrayOf(0x00), MqttClient.encodeRemainingLength(0))
        assertArrayEquals(byteArrayOf(0x7F), MqttClient.encodeRemainingLength(127))
    }

    @Test fun remainingLengthTwoBytes() {
        // 128 -> 0x80 0x01, per the MQTT 3.1.1 spec example.
        assertArrayEquals(byteArrayOf(0x80.toByte(), 0x01), MqttClient.encodeRemainingLength(128))
        // 321 -> 0xC1 0x02
        assertArrayEquals(byteArrayOf(0xC1.toByte(), 0x02), MqttClient.encodeRemainingLength(321))
    }

    @Test fun remainingLengthMaxFourBytes() {
        assertArrayEquals(
            byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x7F),
            MqttClient.encodeRemainingLength(268_435_455),
        )
    }
}
