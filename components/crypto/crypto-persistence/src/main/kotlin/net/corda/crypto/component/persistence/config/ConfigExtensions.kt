package net.corda.crypto.component.persistence.config

import com.typesafe.config.ConfigFactory
import net.corda.libs.configuration.SmartConfig


val SmartConfig.softPersistence: CryptoPersistenceConfig get() =
    if(hasPath(this::softPersistence.name)) {
        CryptoPersistenceConfig(getConfig(this::softPersistence.name))
    } else {
        CryptoPersistenceConfig(factory.create(ConfigFactory.empty()))
    }


val SmartConfig.signingPersistence: CryptoPersistenceConfig get() =
    if(hasPath(this::signingPersistence.name)) {
        CryptoPersistenceConfig(getConfig(this::signingPersistence.name))
    } else {
        CryptoPersistenceConfig(factory.create(ConfigFactory.empty()))
    }