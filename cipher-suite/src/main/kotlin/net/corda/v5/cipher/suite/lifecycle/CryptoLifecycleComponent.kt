package net.corda.v5.cipher.suite.lifecycle

import net.corda.v5.cipher.suite.config.CryptoLibraryConfig

interface CryptoLifecycleComponent {
    fun handleConfigEvent(config: CryptoLibraryConfig)
}