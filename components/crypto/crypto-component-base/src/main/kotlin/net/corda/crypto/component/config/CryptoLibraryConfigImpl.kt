package net.corda.crypto.component.config

import net.corda.crypto.impl.config.CryptoConfigMap
import net.corda.v5.cipher.suite.config.CryptoLibraryConfig

class CryptoLibraryConfigImpl(
    map: Map<String, Any?>
) : CryptoConfigMap(map), CryptoLibraryConfig


