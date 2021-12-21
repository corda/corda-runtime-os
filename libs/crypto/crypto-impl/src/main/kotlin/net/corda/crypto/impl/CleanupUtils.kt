@file:JvmName("CleanupUtils")
package net.corda.crypto.impl

import net.corda.lifecycle.Lifecycle

fun AutoCloseable.closeGracefully() {
    try {
        close()
    } catch (e: Throwable) {
        // intentional
    }
}

fun Lifecycle.stopGracefully() {
    try {
        stop()
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