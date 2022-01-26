@file:JvmName("CleanupUtils")
package net.corda.crypto.impl

fun MutableMap<*, *>.clearCache() {
    forEach {
        (it.value as? AutoCloseable)?.close()
    }
    clear()
}