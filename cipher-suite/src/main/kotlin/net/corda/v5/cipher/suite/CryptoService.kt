package net.corda.v5.cipher.suite

import net.corda.v5.cipher.suite.schemes.SignatureScheme
import net.corda.v5.crypto.exceptions.CryptoServiceBadRequestException
import net.corda.v5.crypto.exceptions.CryptoServiceException

/**
 * Crypto service which can sign as well as create new key pairs.
 *
 * Note about key aliases. Corda always uses single alias to identify a key pair however some HSMs need separate
 * aliases for public and private keys, in such cases their names have to be derived from the single key pair alias.
 * It could be suffixes or whatever internal naming scheme is used.
 */
interface CryptoService {
    /**
     * Returns true if the createWrappingKey operation is required.
     * The wrapping key may not be required in situations when HSM supports the wrapped keys natively or
     * wrapping key is global.
     */
    fun requiresWrappingKey(): Boolean

    /**
     * Signature schemes which this implementation of [CryptoService] supports.
     * */
    fun supportedSchemes(): Array<SignatureScheme>

    /**
     * Generates a new key to be used as a wrapping key. Some implementations may not have the notion of
     * the wrapping key in such cases the implementation should do nothing (note that [requiresWrappingKey] should
     * return false for such implementations).
     *
     * @param masterKeyAlias the alias of the key to be used as a wrapping key.
     * @param failIfExists a flag indicating whether the method should fail if a key already exists under
     * the provided alias or return normally without overriding the key.
     * @param context the optional key/value operation context.
     *
     * @throws [CryptoServiceBadRequestException] if a key already exists under this alias
     * and [failIfExists] is set to true.
     * @throws [CryptoServiceException] for general cryptographic exceptions.
     */
    fun createWrappingKey(
        masterKeyAlias: String,
        failIfExists: Boolean,
        context: Map<String, String>
    )

    /**
     * Generate and optionally store an asymmetric key pair.
     *
     * @param spec parameters to generate key pair.
     * @param context the optional key/value operation context.
     *
     * Returns information about the generated key, could be either [GeneratedPublicKey] or [GeneratedWrappedKey]
     *
     * @throws [CryptoServiceBadRequestException] if the [SignatureScheme] is not supported.
     * @throws [CryptoServiceException] for general cryptographic exceptions.
     */
    fun generateKeyPair(
        spec: KeyGenerationSpec,
        context: Map<String, String>
    ): GeneratedKey

    /**
     * Sign a byte array using the private key identified by the input arguments.
     * Returns the signature bytes formatted according to the default signature spec.
     *
     * @param spec (either [SigningAliasSpec] or [SigningWrappedSpec]) to be used for signing.
     * @param data the data to be signed.
     * @param context the optional key/value operation context.
     *
     * @throws [CryptoServiceBadRequestException] if the private key does not exist as defined in the [key],
     * the key scheme is not supported,  or the [data] is empty array.
     * @throws [CryptoServiceException] for general cryptographic exceptions.
     */
    fun sign(
        spec: SigningSpec,
        data: ByteArray,
        context: Map<String, String>
    ): ByteArray
}
