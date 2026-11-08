package hi3.hashkit.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MinerHostValidatorTest {

    @Test
    fun `allows private, CGNAT and link-local ranges`() {
        assertTrue(MinerHostValidator.isAllowedIp("10.0.0.203"))
        assertTrue(MinerHostValidator.isAllowedIp("172.16.5.1"))
        assertTrue(MinerHostValidator.isAllowedIp("172.31.255.254"))
        assertTrue(MinerHostValidator.isAllowedIp("192.168.1.42"))
        assertTrue(MinerHostValidator.isAllowedIp("100.64.0.1"))     // Tailscale CGNAT low
        assertTrue(MinerHostValidator.isAllowedIp("100.127.255.254")) // Tailscale CGNAT high
        assertTrue(MinerHostValidator.isAllowedIp("169.254.10.10"))
        assertTrue(MinerHostValidator.isAllowedIp("127.0.0.1"))
    }

    @Test
    fun `refuses public and malformed addresses`() {
        assertFalse(MinerHostValidator.isAllowedIp("8.8.8.8"))
        assertFalse(MinerHostValidator.isAllowedIp("1.1.1.1"))
        assertFalse(MinerHostValidator.isAllowedIp("172.32.0.1"))   // just past RFC1918
        assertFalse(MinerHostValidator.isAllowedIp("100.128.0.1"))  // just past CGNAT
        assertFalse(MinerHostValidator.isAllowedIp("192.169.0.1"))
        assertFalse(MinerHostValidator.isAllowedIp("256.1.1.1"))
        assertFalse(MinerHostValidator.isAllowedIp("10.0.0"))
        assertFalse(MinerHostValidator.isAllowedIp(""))
        assertFalse(MinerHostValidator.isAllowedIp("pool.example.com"))
    }

    @Test
    fun `only strict dotted-quad literals count as IP literals`() {
        // Kotlin's toIntOrNull accepts these, Bionic's inet_pton does not — they must NOT be
        // treated as allowed literals (they would go to DNS as hostnames instead).
        assertNull(MinerHostValidator.parseIpv4("010.0.0.1"))
        assertNull(MinerHostValidator.parseIpv4("10.0.0.+1"))
        assertNull(MinerHostValidator.parseIpv4("0x0a.0.0.1"))
        assertNull(MinerHostValidator.parseIpv4("10.0.1"))
        assertNull(MinerHostValidator.parseIpv4("10.0.0.1."))
        assertFalse(MinerHostValidator.isAllowedIp("010.0.0.1"))
        assertEquals(listOf(10, 0, 0, 203), MinerHostValidator.parseIpv4("10.0.0.203")!!.toList())
    }

    @Test
    fun `resolved addresses are judged by bytes and v6 fails closed`() {
        assertTrue(MinerHostValidator.isAllowedAddress(java.net.InetAddress.getByName("192.168.1.42")))
        assertTrue(MinerHostValidator.isAllowedAddress(java.net.InetAddress.getByName("100.100.1.1")))
        assertFalse(MinerHostValidator.isAllowedAddress(java.net.InetAddress.getByName("203.0.113.9")))
        assertFalse(MinerHostValidator.isAllowedAddress(java.net.InetAddress.getByName("::1")))
        assertFalse(MinerHostValidator.isAllowedAddress(java.net.InetAddress.getByName("::ffff:10.0.0.1").let {
            // Java normalises v4-mapped to Inet4Address; force a real v6 instance for the check.
            java.net.Inet6Address.getByAddress(null, ByteArray(16).also { b -> b[15] = 1 }, null)
        }))
    }

    @Test
    fun `resolveAllowed short-circuits literals without DNS`() {
        assertEquals("10.0.0.203", MinerHostValidator.resolveAllowed("10.0.0.203")!!.hostAddress)
        assertNull(MinerHostValidator.resolveAllowed("8.8.8.8"))
        assertNull(MinerHostValidator.resolveAllowed(""))
    }
}

class SubnetUtilsTest {

    @Test
    fun `expands a slash24 excluding network and broadcast`() {
        val hosts = SubnetUtils.expand(SubnetUtils.Cidr("192.168.1.0", 24))!!
        assertEquals(254, hosts.size)
        assertEquals("192.168.1.1", hosts.first())
        assertEquals("192.168.1.254", hosts.last())
    }

    @Test
    fun `refuses ranges wider than the safety cap`() {
        assertNull(SubnetUtils.expand(SubnetUtils.Cidr("10.0.0.0", 16)))
        assertNull(SubnetUtils.expand(SubnetUtils.Cidr("10.0.0.0", 21)))
        assertNotNull(SubnetUtils.expand(SubnetUtils.Cidr("10.0.0.0", 22)))
    }

    @Test
    fun `parse rejects public and malformed cidrs`() {
        assertNull(SubnetUtils.parseCidr("8.8.8.0/24"))
        assertNull(SubnetUtils.parseCidr("192.168.1.0"))
        assertNull(SubnetUtils.parseCidr("192.168.1.0/33"))
        assertNull(SubnetUtils.parseCidr("192.168.1.0/7"))
        assertNull(SubnetUtils.parseCidr("garbage"))
        assertNotNull(SubnetUtils.parseCidr("10.0.0.0/24"))
        assertNotNull(SubnetUtils.parseCidr(" 100.64.0.0/24 "))
    }

    @Test
    fun `derives slash24 of an address`() {
        assertEquals(SubnetUtils.Cidr("10.0.0.0", 24), SubnetUtils.slash24Of("10.0.0.26"))
        assertNull(SubnetUtils.slash24Of("8.8.8.8"))
    }
}
