package net.corda.libs.configuration.secret

import com.typesafe.config.Config

interface SecretsCreateService {
    /**
     * Create secret configuration value
     *
     * @param plainText secret
     * @param key a handle that globally uniquely identifies this secret. Implementations of this interface
     *        can use this to differentiate between secrets. This might be important if the secrets go in a key
     *        value store, in order to keep different values separate from each other.
     *        Or, the implementation may ignore this.
     * @return [Config] object that contains everything needed to be able to retrieve the secret.
     */
    fun createValue(plainText: String, key: String): Config
}

