package net.corda.v5.cipher.suite

/**
 * Provides ways of getting the secret value for the [CryptoServiceProvider] from the service configuration.
 * The secret value have to be declared as Map<String, Any> (or Map<String, Object>) in the service
 * configuration class for that to work.
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