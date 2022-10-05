package net.corda.crypto.ecies

import java.security.PublicKey

/**
 * Defines ECIES encryption for the initiator of the communication (e.g. a member wishing to register its details
 * with the network operator).
 * To initiate the secure communication the consumer should get hold of the public key of the other communicating party
 * (network operator) encrypt the data using provided operations in this interface, then the receiving party will
 * use the other service using the [StableKeyPairDecryptor] to decrypt the data.
 * The API assumes one-way communication e.g. member-to-network_operator.
 */
interface EphemeralKeyPairEncryptor {
    /**
     * Encrypts the data. The operation is one-off meaning that the ephemeral private key pair is generated each time
     * and discarded after the encryption is done (together with derived secret) and cannot be reused.
     * The encryption uses AES GCM.
     * The secret used for AES is derived using Diffie–Hellman key agreement with further HKDF transformation using
     * the provided [salt] and the hardcoded info ('Corda key' for the AES key itself and 'Corda iv' for the nonce)
     * with the first 8 bytes of the nonce being XOR with the current timestamp. The info is hardcoded due that
     * the API assumes only one use of the ephemeral key pair so no point to provide them as it's not possible to reuse
     * the keys and secrets. The digest algorithm for HKDF is infered based on the key scheme - e.g. SECP256R1 will
     * use SHA-256 and SECP384 will use SHA-384.
     *
     * @param salt used by the HKDF function and provides source of additional entropy, according to that spec
     * https://datatracker.ietf.org/doc/html/rfc5869#section-3.1 it can be exchanged between
     * communicating parties, the function receives two public keys - the first one is the ephemeral public key
     * generated for this specific operation, and the second is the [otherPublicKey].
     * @param otherPublicKey the public key of the other party with which the consumer wants to establish secure
     * communication, the ephemeral key pair will be generated using the same key scheme. The key scheme of the
     * public key must support Diffie–Hellman key agreement, see [net.corda.v5.cipher.suite.schemes.KeyScheme] and
     * its capabilities' property.
     * @param plainText plain text to be encrypted.
     * @param aad the optional additional authentication data used by the GCM, the provided data will be concatenated
     * with the public keys of the both parties.
     *
     * @return the result of the encryption, including the public key of the generated ephemeral key pair.
     */
    fun encrypt(
        otherPublicKey: PublicKey,
        plainText: ByteArray,
        aad: ByteArray?,
        salt: (PublicKey, PublicKey) -> ByteArray
    ): EncryptedDataWithKey
}