package net.corda.crypto.component.impl

import net.corda.crypto.cipher.suite.ConfigurationSecrets
import net.corda.crypto.cipher.suite.CryptoService

/**
 * Factory to create new instances of the [CryptoService].
 */
interface CryptoServiceProvider<T : Any> {
    /**
     * The name used to resolve current provider by crypto service factory.
     */
    val name: String

    /**
     * Class for crypto service specific configuration which must be defined together with particular [CryptoService]
     * implementation.
     */
    val configType: Class<T>

    /**
     * Creates a new instance of the [CryptoService] implementation.
     *
     * @param config crypto service configuration
     * @param secrets provides access to decrypting the configuration secrets
     *
     * The secrets have to be declared as Map in the corresponding POJO, the JSON will look like
     * the example below for the property called 'passphrase'
     *
     * POJO (Kotlin):
     *```
     *  val passphrase: Map<String, Any>
     *```
     *
     * JSON:
     *```
     *  "passphrase": {
     *      "configSecret": {
     *          "encryptedSecret": "<encrypted-value>"
     *      }
     *  }
     *```
     *
     * @return An instance of the [CryptoService].
     *
     * @throws [net.corda.v5.crypto.exceptions.CryptoException] for general cryptographic exceptions.
     */
    fun getInstance(config: T, secrets: ConfigurationSecrets): CryptoService
}
