package com.akshay.musicplayer.data.remote.stream

import okhttp3.Dns
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * Ensures requests to Google Video CDN edge nodes (*.googlevideo.com) connect
 * using the exact same IP address family (IPv4 or IPv6) that was bound to the
 * signed stream URL token (&ip=...).
 *
 * Connecting over IPv6 to a URL signed for an IPv4 address (or vice-versa) results
 * in an immediate HTTP 403 Forbidden rejection by Google Video CDN.
 */
object GoogleVideoDnsHelper {
    // 4 for IPv4 only, 6 for IPv6 only, null for system default
    private val preferredFamily = ThreadLocal<Int?>()

    fun <T> withPreferredFamily(family: Int?, block: () -> T): T {
        val previous = preferredFamily.get()
        preferredFamily.set(family)
        try {
            return block()
        } finally {
            preferredFamily.set(previous)
        }
    }

    val currentFamily: Int?
        get() = preferredFamily.get()
}

class GoogleVideoDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = Dns.SYSTEM.lookup(hostname)
        if (hostname.contains("googlevideo.com")) {
            val family = GoogleVideoDnsHelper.currentFamily
            if (family == 4) {
                val v4 = addresses.filterIsInstance<Inet4Address>()
                if (v4.isNotEmpty()) return v4
            } else if (family == 6) {
                val v6 = addresses.filterIsInstance<Inet6Address>()
                if (v6.isNotEmpty()) return v6
            }
        }
        return addresses
    }
}
