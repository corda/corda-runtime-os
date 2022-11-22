package net.corda.crypto.hes

import java.security.PublicKey

/**
 * The result of the encryption using the shared secret. The encryption operation is a one-off meaning that the
 * ephemeral key pair is generated for each encryption operation.
 *
 * @param publicKey the public key of the generated ephemeral pair, note that the lifetime of the private key of the pair
 * and the shared secret generated using that key does not exceed the scope of the encrypting function.
 * @param salt the salt which was used for deriving the shared secret.
 * @param aad the aad which was provided in the AEAD encryption cipher.
 * @param cipherText the resulting cipher text where the first 8 bytes are the current epoch timestamp in mills when
 * the encryption was done.
 */
class EncryptedDataWithKey(
    val publicKey: PublicKey,
    val cipherText: ByteArray,
    val params: HybridEncryptionParams
)