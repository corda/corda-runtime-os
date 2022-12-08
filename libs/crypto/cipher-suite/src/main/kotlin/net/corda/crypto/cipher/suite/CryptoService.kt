package net.corda.crypto.cipher.suite

import net.corda.crypto.cipher.suite.schemes.KeyScheme
import net.corda.v5.crypto.SignatureSpec

/**
 * Crypto service which can be used to sign and generate new key pairs.
 *
 * Corda always uses a single alias to identify a key pair. However, some HSMs need separate aliases for public
 * and private keys. In such cases, their names must be derived from the single key pair alias.
 * It could be suffixes or whatever internal naming convention is used.
 *
 * Also, it is not required to keep a public key in the HSM as that will be kept by the upstream Crypto Services.
 *
 * # Exception handling.
 *
 * As the service instances are decorated with decorators taking care of the timeout, throttling handling,
 * and exception normalisation the exceptions listed for each method below are as expected to be thrown by the decorators.
 *
 * The service should throw the most appropriate exception as it sees it, e.g.:
 * - [IllegalArgumentException] if the key is not found, the key scheme is not supported, the signature spec
 * is not supported or in general the input parameters are wrong
 * - if the internal state is wrong for an operation then the most appropriate exception would be [IllegalStateException]
 * - any other that is appropriate for the condition.
 *
 * If service encountered throttling situation and the downstream library doesn't handle that, it should throw
 * [net.corda.v5.crypto.exceptions.CryptoThrottlingException]
 *
 * The following exceptions are retried - [java.util.concurrent.TimeoutException],
 * [net.corda.v5.crypto.exceptions.CryptoException] (with the isRecoverable flag set to true),
 * some persistence exceptions like [javax.persistence.LockTimeoutException], [javax.persistence.QueryTimeoutException],
 * [javax.persistence.OptimisticLockException], [javax.persistence.PessimisticLockException] and some others.
 * Throw [java.util.concurrent.TimeoutException] only if the service should handle it before the upstream
 * library detects it.
 *
 * # About service extensions.
 *
 * The implementation of the [CryptoService] consists of the method of the implementation required and some optional
 * methods, which are called extensions. It's done this way, instead of breaking the functionality into several
 * interfaces, because the platform uses decorators to handle some common failure scenarios where using extension
 * interfaces would be awkward. If not stated explicitly in the description, the method is required.
 */
interface CryptoService {
    /**
     * List of crypto service extensions which are supported by this implementation of [CryptoService],
     * such as REQUIRE_WRAPPING_KEY, DELETE_KEYS.
     */
    val extensions: List<CryptoServiceExtensions>

    /**
     * List of key schemes and signature specs for each key which this implementation of [CryptoService] supports.
     */
    val supportedSchemes: Map<KeyScheme, List<SignatureSpec>>

    /**
     * Generates and optionally stores a key pair. The implementation is free to decide how the generated key
     * is stored - either in the corresponding HSM or wrapped and exported. The rule of thumb would be in the [spec]
     * has the [KeyGenerationSpec.alias] defined then it's expected that the key will be stored in the HSM otherwise
     * wrapped and exported but as mentioned above it's up to the concrete implementation. Such behaviour must be
     * defined beforehand and advertised. If the key is exported, its key material will be persisted
     * on the platform side.
     *
     * @param spec parameters to generate the key pair.
     * @param context the optional key/value operation context. The context will have at least two variables defined -
     * 'tenantId' and 'category'.
     *
     * @return Information about the generated key, could be either [GeneratedPublicKey] or [GeneratedWrappedKey]
     * depending on how the key is generated and persisted or wrapped and exported.
     *
     * @throws IllegalArgumentException the key scheme is not supported or in general the input parameters are wrong
     * @throws net.corda.v5.crypto.exceptions.CryptoException, non-recoverable
     */
    fun generateKeyPair(
        spec: KeyGenerationSpec,
        context: Map<String, String>
    ): GeneratedKey

    /**
     * Signs a byte array using the private key identified by the input arguments.
     *
     * @param spec (either [SigningAliasSpec] or [SigningWrappedSpec]) to be used for signing.
     * @param data the data to be signed.
     * @param context the optional key/value operation context. The context will have at least one variable defined -
     * 'tenantId'.
     *
     * @return the signature bytes formatted according to the default signature spec.
     *
     * @throws IllegalArgumentException if the key is not found, the key scheme is not supported, the signature spec
     * is not supported or in general the input parameters are wrong
     * @throws net.corda.v5.crypto.exceptions.CryptoException, non-recoverable
     */
    fun sign(
        spec: SigningSpec,
        data: ByteArray,
        context: Map<String, String>
    ): ByteArray

    /**
     * Optional, generates a new key to be used as a wrapping key. Some implementations may not have the notion of
     * the wrapping key in such cases the implementation should do nothing (note that REQUIRE_WRAPPING_KEY should not
     * be listed for such implementations).
     *
     * @param masterKeyAlias the alias of the key to be used as a wrapping key.
     * @param failIfExists a flag indicating whether the method should fail if a key already exists under
     * the provided alias or return normally without overriding the key.
     * @param context the optional key/value operation context.
     *
     * @throws IllegalArgumentException if the [failIfExists] is set to true and the key exists
     * @throws UnsupportedOperationException if the operation is not supported
     * @throws net.corda.v5.crypto.exceptions.CryptoException, non-recoverable
     */
    fun createWrappingKey(
        masterKeyAlias: String,
        failIfExists: Boolean,
        context: Map<String, String>
    )

    /**
     * Optional, deletes the key corresponding to the input alias of the service supports the operations .
     * This method doesn't throw if the alias is not found, instead it has to return 'false'.
     *
     * @param alias the alias (as it stored in HSM) of the key being deleted.
     * @param context the optional key/value operation context. The context will have at least two variables defined -
     * 'tenantId' and 'keyType'.
     *
     * @return true if the key was deleted false otherwise
     *
     * @throws UnsupportedOperationException if the operation is not supported
     * @throws net.corda.v5.crypto.exceptions.CryptoException, non-recoverable
     */
    fun delete(alias: String, context: Map<String, String>): Boolean

    /**
     * Optional, derives Diffie–Hellman key agreement shared secret by using the private key associated
     * with [SharedSecretSpec.publicKey] and [SharedSecretSpec.otherPublicKey], note that the key schemes
     * of the [SharedSecretSpec.publicKey] and [SharedSecretSpec.otherPublicKey] must be the same and
     * the scheme must support the key agreement secret derivation.
     *
     * @param spec the operation parameters, see [SharedSecretAliasSpec] and [SharedSecretWrappedSpec]
     * @param context the optional key/value operation context. The context will have at least one variable defined -
     * 'tenantId'.
     *
     * @return the shared secret. Note that it's expected that the returned secret must be not post processed by
     * applying further transformations (e.g. HKDF or any other) as that will be done by Corda itself.
     *
     * @throws IllegalArgumentException if the key is not found, the key scheme is not supported, the signature spec
     * is not supported, the key schemes of the public keys are not matching or in general the input parameters are wrong
     * @throws UnsupportedOperationException if the operation is not supported
     * @throws net.corda.v5.crypto.exceptions.CryptoException, non-recoverable
     */
    fun deriveSharedSecret(
        spec: SharedSecretSpec,
        context: Map<String, String>
    ): ByteArray
}
