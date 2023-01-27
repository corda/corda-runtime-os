package net.corda.libs.configuration.secret

import com.typesafe.config.Config

interface SecretsCreateService {
    /**
     * Create secret configuration value
     *
     * @param plainText secret
     * @param key cluster wide unique handle on the secret, e.g. `master_wrapping_key_passphrase` or `vnode_vault_db_<hash>`
     * @return [Config] object that contains everything needed to be able to retrieve the secret.
     */
    fun createValue(plainText: String, key: String): Config
}

