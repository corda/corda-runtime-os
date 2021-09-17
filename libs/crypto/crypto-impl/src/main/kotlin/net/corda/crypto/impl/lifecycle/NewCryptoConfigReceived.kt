package net.corda.crypto.impl.lifecycle

import net.corda.crypto.impl.config.CryptoLibraryConfig
import net.corda.lifecycle.LifecycleEvent

data class NewCryptoConfigReceived(
    val config: CryptoLibraryConfig
) : LifecycleEvent