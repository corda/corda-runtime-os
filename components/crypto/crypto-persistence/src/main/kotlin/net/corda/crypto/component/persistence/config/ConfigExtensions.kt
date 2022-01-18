package net.corda.crypto.component.persistence.config

import net.corda.crypto.impl.config.CryptoConfigMap
import net.corda.v5.cipher.suite.config.CryptoLibraryConfig


val CryptoLibraryConfig.softPersistence: CryptoPersistenceConfig
    get() =
        CryptoPersistenceConfig(CryptoConfigMap.getOptionalConfig(this, this::softPersistence.name) ?: emptyMap())


val CryptoLibraryConfig.signingPersistence: CryptoPersistenceConfig
    get() =
        CryptoPersistenceConfig(CryptoConfigMap.getOptionalConfig(this, this::signingPersistence.name) ?: emptyMap())