package io.cttech.lighthouse.system

import java.net.InetAddress
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TailscaleTest {
    private fun tailnet(ip: String) = Tailscale.isTailnetAddress(InetAddress.getByName(ip))

    @Test fun cgnatRangeIsTailnet() {
        assertTrue(tailnet("100.64.0.1"))
        assertTrue(tailnet("100.83.73.32"))
        assertTrue(tailnet("100.127.255.254"))
    }

    @Test fun neighboursOfTheRangeAreNot() {
        assertFalse(tailnet("100.63.255.255"))
        assertFalse(tailnet("100.128.0.1"))
        assertFalse(tailnet("10.10.1.20"))
        assertFalse(tailnet("192.168.1.1"))
    }

    @Test fun tailscaleIpv6PrefixIsTailnet() {
        assertTrue(tailnet("fd7a:115c:a1e0::1"))
        assertTrue(tailnet("fd7a:115c:a1e0:ab12:4843:cd96:6253:4920"))
        assertFalse(tailnet("fd7a:115c:a1e1::1"))
        assertFalse(tailnet("fe80::1"))
    }
}
