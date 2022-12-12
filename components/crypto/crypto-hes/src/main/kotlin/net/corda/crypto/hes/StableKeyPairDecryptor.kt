package net.corda.crypto.hes

import net.corda.lifecycle.Lifecycle
import java.security.PublicKey

/**
 * Defines hybrid decryption for the receiver of the communication (e.g. a network operator receiving request from
 * a member wishing to register its details with the network operator).
 * The API assumes that the key pair owned by the receiving party is stable and not generated on each invocation.
 * However, it's expected that the key pair of the other party is ephemeral.
 * To initiate the secure communication the consumer should get hold of the public key of the other communicating party
 * (network operator) encrypt the data using provided operations in this interface, then the receiving party will
 * use the other service using the [StableKeyPairDecryptor] to decrypt the data.
 * The API assumes one-way communication e.g. member-to-network_operator.
 */
interface StableKeyPairDecryptor : Lifecycle {
    /**
     * Decrypts the data. The derived secret is discarded after each operation.
     * The encryption uses AES GCM.
     * The secret used for AES is derived using Diffie–Hellman key agreement with further HKDF transformation using
     * the provided [salt] and the hardcoded info ('Corda key' for the AES key itself and 'Corda iv' for the nonce)
     * with the first 8 bytes of the nonce being XOR with the current timestamp. The info is hardcoded due that
     * the API assumes only one use of the ephemeral key pair so no point to provide them as it's not possible to reuse
     * the keys and secrets. The digest algorithm for HKDF is infered based on the key scheme - e.g. SECP256R1 will
     * use SHA-256 and SECP384 will use SHA-384.
     *
     * The key schemes of the [publicKey] and [otherPublicKey] must be the same.
     *
     * @param tenantId the tenant owning the stable key pair (receiver)
     * @param salt used by the HKDF function and provides source of additional entropy, according to that spec
     * https://datatracker.ietf.org/doc/html/rfc5869#section-3.1 it can be exchanged between
     * communicating parties
     * @param publicKey the public key of the receiving party, the private key is managed by the Crypto Library and
     * is not directly accessible. The key scheme of the public key must support Diffie–Hellman key agreement,
     * see [net.corda.crypto.cipher.suite.schemes.KeyScheme] and its capabilities' property.
     * @param otherPublicKey the public key of the other party with which the consumer wants to establish secure
     * communication, the ephemeral key pair will be generated using the same key scheme.
     * @param cipherText cipher text to be decrypted.
     * @param aad the optional additional authentication data used by the GCM.
     *
     * @return the original plain text.
     */
    @Suppress("LongParameterList")
    fun decrypt(
        tenantId: String,
        salt: ByteArray,
        publicKey: PublicKey,
        otherPublicKey: PublicKey,
        cipherText: ByteArray,
        aad: ByteArray?
    ): ByteArray
}