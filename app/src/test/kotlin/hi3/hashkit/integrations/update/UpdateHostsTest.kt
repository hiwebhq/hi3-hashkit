package hi3.hashkit.integrations.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateHostsTest {

    @Test
    fun `accepts github and its subdomains over https`() {
        assertTrue(UpdateHosts.isTrustedUrl("https://github.com/hiwebhq/hi3-hashkit/releases/download/v1/a.apk"))
        assertTrue(UpdateHosts.isTrustedUrl("https://api.github.com/repos/x/y/releases/latest"))
        assertTrue(UpdateHosts.isTrustedUrl("https://objects.githubusercontent.com/github-production-release-asset/x"))
        assertTrue(UpdateHosts.isTrustedUrl("https://GitHub.com/x"))
    }

    @Test
    fun `rejects lookalike hosts`() {
        assertFalse(UpdateHosts.isTrustedUrl("https://evilgithub.com/x.apk"))
        assertFalse(UpdateHosts.isTrustedUrl("https://notgithubusercontent.com/x.bin"))
        assertFalse(UpdateHosts.isTrustedUrl("https://github.com.evil.example/x.apk"))
        assertFalse(UpdateHosts.isTrustedHost("github.comevil"))
    }

    @Test
    fun `rejects non-https, userinfo tricks and garbage`() {
        assertFalse(UpdateHosts.isTrustedUrl("http://github.com/x.apk"))
        assertFalse(UpdateHosts.isTrustedUrl("https://github.com@evil.example/x.apk"))
        assertFalse(UpdateHosts.isTrustedUrl("ftp://github.com/x"))
        assertFalse(UpdateHosts.isTrustedUrl(""))
        assertFalse(UpdateHosts.isTrustedUrl("not a url"))
        assertFalse(UpdateHosts.isTrustedHost(null))
    }
}
