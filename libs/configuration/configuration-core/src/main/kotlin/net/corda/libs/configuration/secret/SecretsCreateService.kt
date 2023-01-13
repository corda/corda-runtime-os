package net.corda.libs.configuration.secret

import com.typesafe.config.Config

interface SecretsCreateService {
    /**
     * Create secret configuration value
     *
     * @param plainText secret
     * @return [Config] object that contains everything needed to be able to retrieve the secret.
     */
    fun createValue(plainText: String): Config
}

