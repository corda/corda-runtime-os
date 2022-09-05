package net.corda.v5.cipher.suite

/**
 * Factory to create new instances of the [SoftKeyWrappingService].
 */
interface SoftKeyWrappingServiceProvider<T : Any> {
    /**
     * The name used to resolve current provider by crypto service factory.
     */
    val name: String

    /**
     * Class for encryption service specific configuration which must be defined together with particular [CryptoService]
     * implementation.
     */
    val configType: Class<T>

    /**
     * Returns an instance of the [SoftKeyWrappingService].
     * @param config crypto service configuration
     * @param secrets provides access to decrypting the secrets
     *
     * The secrets have to be declared as Map<String, Any> in the corresponding POJO, the JSON will look like
     * in the example bellow for the property called 'passphrase'
     *
     * POJO (Kotlin):
     *```
     *  val passphrase: Map<String, Any>
     *```
     * JSON:
     *```
     *  "passphrase": {
     *      "configSecret": {
     *          "encryptedSecret": "<encrypted-value>"
     *      }
     *  }
     *```
     * @throws [net.corda.v5.crypto.exceptions.CryptoException] for general cryptographic exceptions.
     */
    fun getInstance(config: T, secrets: ConfigurationSecrets): CryptoService
}