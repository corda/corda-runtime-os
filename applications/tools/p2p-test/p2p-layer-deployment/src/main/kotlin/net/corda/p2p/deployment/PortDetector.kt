package net.corda.p2p.deployment

import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger

class PortDetector{
    private val port = AtomicInteger(3000)

    fun next(): Int {
        while (!testPort()) {
            port.incrementAndGet()
        }
        return port.getAndIncrement()
    }

    fun testPort(): Boolean {
        return try {
            ServerSocket().use {
                it.bind(InetSocketAddress(port.get()))
            }
            true
        } catch (e: IOException) {
            false
        }
    }
}
