package net.corda.crypto.cipher.suite

/**
 * Provides ways of getting the secret value for the [CryptoServiceProvider] from the service configuration.
 * The secret value must be declared as Map (or Map) in the service configuration class.
 */
interface ConfigurationSecrets {
    /**
     * @param secret encrypted secret
     *
     * @return the plain text secret's value.
     *
     * @see CryptoServiceProvider.getInstance
     */
    fun getSecret(secret: Map<String, Any>): String
}