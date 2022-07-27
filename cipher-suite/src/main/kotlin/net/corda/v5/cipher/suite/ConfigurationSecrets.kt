package net.corda.v5.cipher.suite

/**
 * Provides ways of getting the secret's value as that is encrypted in the configuration.
 * The secret value have to be declared as Map<String, Any> (or Map<String, Object>) for that to work.
 */
interface ConfigurationSecrets {
    fun getSecret(secret: Map<String, Any>): String
}