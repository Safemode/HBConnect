package com.safemode.hbconnect.ui.server

/**
 * True when [rawHost] looks like it's on the user's own network (or a private overlay network), where
 * plain HTTP is a reasonable choice: localhost, private and link-local IP ranges, Tailscale's CGNAT
 * range and MagicDNS names, single-label names, and common local suffixes (.local, .lan, ...). Used
 * only to decide whether to warn about HTTP, so it errs toward "local" for names it can't judge.
 */
fun isLocalNetworkHost(rawHost: String): Boolean {
    val host = rawHost.trim().lowercase()
        .removePrefix("http://")
        .removePrefix("https://")
        .substringBefore('/')
        .let { h ->
            when {
                h.startsWith("[") -> h.substringAfter('[').substringBefore(']') // [ipv6]:port
                h.count { it == ':' } == 1 -> h.substringBefore(':') // host:port
                else -> h
            }
        }
        .trimEnd('.')
    if (host.isBlank()) return true
    if (host == "localhost" || host.endsWith(".localhost")) return true
    if (LOCAL_SUFFIXES.any { host.endsWith(it) }) return true

    // IPv6: loopback, unique-local (fc00::/7) and link-local (fe80::/10).
    if (':' in host) {
        return host == "::1" || host.startsWith("fc") || host.startsWith("fd") || host.startsWith("fe8") ||
            host.startsWith("fe9") || host.startsWith("fea") || host.startsWith("feb")
    }

    val octets = host.split('.').map { it.toIntOrNull() }
    if (octets.size == 4 && octets.all { it != null && it in 0..255 }) {
        val (a, b) = octets[0]!! to octets[1]!!
        return a == 10 || a == 127 ||
            (a == 172 && b in 16..31) ||
            (a == 192 && b == 168) ||
            (a == 169 && b == 254) ||
            (a == 100 && b in 64..127) // CGNAT, used by Tailscale
    }

    // A bare name like "homebox" only resolves on a local network.
    return '.' !in host
}

private val LOCAL_SUFFIXES = listOf(".local", ".lan", ".home", ".home.arpa", ".internal", ".intranet", ".ts.net")
