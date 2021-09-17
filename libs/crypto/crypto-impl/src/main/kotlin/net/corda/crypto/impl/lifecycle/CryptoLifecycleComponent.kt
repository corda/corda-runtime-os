package net.corda.crypto.impl.lifecycle

import net.corda.lifecycle.Lifecycle

// TODO2 - move to the cipher-suite module in corda-api repo
// (together with NewCryptoConfigReceived and CryptoLibraryConfig)
interface CryptoLifecycleComponent : Lifecycle {
    fun handleConfigEvent(event: NewCryptoConfigReceived)
}

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