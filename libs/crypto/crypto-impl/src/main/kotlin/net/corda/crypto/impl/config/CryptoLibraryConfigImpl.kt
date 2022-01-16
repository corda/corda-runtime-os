package net.corda.crypto.impl.config

import net.corda.crypto.impl.config.CryptoConfigMap.Companion.getOptionalConfig
import net.corda.v5.cipher.suite.config.CryptoLibraryConfig

class CryptoLibraryConfigImpl(
    map: Map<String, Any?>
) : CryptoConfigMap(map), CryptoLibraryConfig

val CryptoLibraryConfig.isDev: Boolean get() =
    CryptoConfigMap.getBoolean(this, this::isDev.name, false)

val CryptoLibraryConfig.softCryptoService: CryptoPersistenceConfig get() =
    CryptoPersistenceConfig(getOptionalConfig(this, this::softCryptoService.name) ?: emptyMap())

val CryptoLibraryConfig.publicKeys: CryptoPersistenceConfig get() =
    CryptoPersistenceConfig(getOptionalConfig(this, this::publicKeys.name) ?: emptyMap())

val CryptoLibraryConfig.memberConfig: CryptoConfigMap get() =
        getOptionalConfig(this, this::memberConfig.name) ?: CryptoConfigMap(emptyMap())


