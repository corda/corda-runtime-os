package net.corda.crypto.impl.config

import net.corda.crypto.impl.config.CryptoConfigMap.Companion.getOptionalConfig
import net.corda.v5.cipher.suite.config.CryptoLibraryConfig

class CryptoLibraryConfigImpl(
    map: Map<String, Any?>
) : CryptoConfigMap(map), CryptoLibraryConfig

val CryptoLibraryConfig.isDev: Boolean get() =
    CryptoConfigMap.getBoolean(this, this::isDev.name, false)

val CryptoLibraryConfig.keyCache: CryptoPersistenceConfig get() =
    CryptoPersistenceConfig(getOptionalConfig(this, this::keyCache.name) ?: emptyMap())

val CryptoLibraryConfig.mngCache: CryptoPersistenceConfig get() =
    CryptoPersistenceConfig(getOptionalConfig(this, this::mngCache.name) ?: emptyMap())

val CryptoLibraryConfig.memberConfig: CryptoConfigMap get() =
        getOptionalConfig(this, this::memberConfig.name) ?: CryptoConfigMap(emptyMap())

val CryptoLibraryConfig.cipherSuite: CipherSuiteConfig
    get() = CipherSuiteConfig(getOptionalConfig(this, this::cipherSuite.name) ?: emptyMap())

