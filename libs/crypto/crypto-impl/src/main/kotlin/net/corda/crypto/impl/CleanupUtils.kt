@file:JvmName("CleanupUtils")
package net.corda.crypto.impl

@Suppress("TooGenericExceptionCaught")
fun AutoCloseable.closeGracefully() {
    try {
        close()
    } catch (e: Throwable) {
        // intentional
    }
}

fun MutableMap<*, *>.clearCache() {
    forEach {
        (it.value as? AutoCloseable)?.closeGracefully()
    }
    clear()
}