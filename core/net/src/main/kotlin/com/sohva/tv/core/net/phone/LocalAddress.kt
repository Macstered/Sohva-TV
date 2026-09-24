package com.sohva.tv.core.net.phone

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

/** The TV's address on the home network, which the phone page is served on (spec 11 PHONE-FR-10). */
object LocalAddress {
    /** One network interface as the picker sees it. */
    data class Candidate(val name: String, val up: Boolean, val loopback: Boolean, val addresses: List<InetAddress>)

    private val skipped = listOf("tun", "ppp", "rmnet")
    private val preferred = listOf("wlan", "eth")

    /**
     * The first site-local IPv4 address (10/8, 172.16/12, 192.168/16) of an interface that is up
     * and not loopback, `wlan*`/`eth*` first; VPN and mobile interfaces (`tun*`, `ppp*`, `rmnet*`)
     * are skipped (rebuild rule). Null: no address to share.
     */
    fun pick(candidates: List<Candidate>): Inet4Address? = candidates
        .filter { c -> c.up && !c.loopback && skipped.none { c.name.startsWith(it) } }
        .sortedBy { c -> if (preferred.any { c.name.startsWith(it) }) 0 else 1 }
        .firstNotNullOfOrNull { c -> c.addresses.firstOrNull { it is Inet4Address && it.isSiteLocalAddress } as Inet4Address? }

    /** The device's interfaces now; on the io dispatcher (it reads the system). */
    fun current(): Inet4Address? {
        val interfaces = runCatching { NetworkInterface.getNetworkInterfaces()?.toList() }.getOrNull().orEmpty()
        return pick(
            interfaces.map { nif ->
                Candidate(nif.name, runCatching { nif.isUp }.getOrDefault(false), runCatching { nif.isLoopback }.getOrDefault(true), nif.inetAddresses.toList())
            },
        )
    }
}
