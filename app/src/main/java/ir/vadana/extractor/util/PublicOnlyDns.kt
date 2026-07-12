package ir.vadana.extractor.util

import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException

class PublicOnlyDns(
    private val delegate: Dns = Dns.SYSTEM,
) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        if (hostname.equals("localhost", ignoreCase = true) ||
            hostname.endsWith(".local", ignoreCase = true) ||
            hostname.endsWith(".internal", ignoreCase = true) ||
            hostname.endsWith(".lan", ignoreCase = true)
        ) {
            throw UnknownHostException("Local hosts are not allowed")
        }

        val addresses = delegate.lookup(hostname)
        if (addresses.isEmpty() || addresses.any { !it.isPublicAddress() }) {
            throw UnknownHostException("Private or reserved address is not allowed")
        }
        return addresses
    }
}

private fun InetAddress.isPublicAddress(): Boolean {
    if (isAnyLocalAddress || isLoopbackAddress || isLinkLocalAddress ||
        isSiteLocalAddress || isMulticastAddress
    ) return false

    val bytes = address
    if (bytes.size == 4) {
        val a = bytes[0].toInt() and 0xff
        val b = bytes[1].toInt() and 0xff
        if (a == 0 || a == 10 || a == 127 || a >= 224) return false
        if (a == 100 && b in 64..127) return false
        if (a == 169 && b == 254) return false
        if (a == 172 && b in 16..31) return false
        if (a == 192 && b == 168) return false
        if (a == 198 && b in 18..19) return false
    } else if (bytes.size == 16) {
        val first = bytes[0].toInt() and 0xff
        val second = bytes[1].toInt() and 0xff
        if ((first and 0xfe) == 0xfc) return false // fc00::/7
        if (first == 0xfe && (second and 0xc0) == 0x80) return false // fe80::/10
    }
    return true
}
