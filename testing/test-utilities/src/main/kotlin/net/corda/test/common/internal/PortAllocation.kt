package net.corda.test.common.internal

import net.corda.v5.base.util.NetworkHostAndPort
import net.corda.v5.base.annotations.DoNotImplement

@DoNotImplement
// Unfortunately cannot be an interface due to `defaultAllocator`
abstract class PortAllocation {

    companion object {
        val defaultAllocator: PortAllocation = SharedMemoryIncrementalPortAllocation.INSTANCE
        const val DEFAULT_START_PORT = 10_000
        const val FIRST_EPHEMERAL_PORT = 30_000
    }

    /** Get the next available port via [nextPort] and then return a [NetworkHostAndPort] **/
    fun nextHostAndPort(): NetworkHostAndPort = NetworkHostAndPort("localhost", nextPort())

    abstract fun nextPort(): Int
}

fun incrementalPortAllocation(): PortAllocation {
    return PortAllocation.defaultAllocator
}