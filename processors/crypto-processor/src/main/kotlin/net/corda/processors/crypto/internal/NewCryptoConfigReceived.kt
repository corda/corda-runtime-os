package net.corda.processors.crypto.internal

import net.corda.lifecycle.LifecycleEvent
import net.corda.v5.cipher.suite.config.CryptoLibraryConfig

data class NewCryptoConfigReceived(
    val config: CryptoLibraryConfig
) : LifecycleEvent