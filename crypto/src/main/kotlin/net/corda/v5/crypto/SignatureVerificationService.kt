package net.corda.v5.crypto

import java.security.InvalidKeyException
import java.security.PublicKey
import java.security.SignatureException

/**
 * The [SignatureVerificationService] provides operation support for [TransactionSignatureVerificationService]
 * with base digital signature verification operations.
 */
interface SignatureVerificationService {
    /**
     * Verifies a digital signature by identifying the signature scheme used from the input public key's type.
     * Always throws an exception if verification fails.
     * Strategy on identifying the actual signing scheme is based on the [PublicKey] type, but if the schemeCodeName is known,
     * then better use doVerify(schemeCodeName: String, publicKey: PublicKey, signatureData: ByteArray, clearData: ByteArray).
     *
     * @param publicKey the signer's [PublicKey].
     * @param signatureData the signatureData on a message.
     * @param clearData the clear data/message that was signed (usually the Merkle root).
     * @throws InvalidKeyException if the key is invalid.
     * @throws SignatureException  if verification fails.
     * @throws IllegalArgumentException if the signature scheme is not supported or if any of the clear or signature data is empty.
     */
    fun verify(publicKey: PublicKey, signatureData: ByteArray, clearData: ByteArray)

    /**
     * Verifies a digital signature by using [signatureSpec].
     * Always throws an exception if verification fails.
     * Strategy on identifying the actual signing scheme is based on the [PublicKey] type, but if the schemeCodeName is known,
     * then better use doVerify(schemeCodeName: String, publicKey: PublicKey, signatureData: ByteArray, clearData: ByteArray).
     *
     * @param publicKey the signer's [PublicKey].
     * @param signatureData the signatureData on a message.
     * @param signatureSpec the signature spec.
     * @param clearData the clear data/message that was signed (usually the Merkle root).
     * @throws InvalidKeyException if the key is invalid.
     * @throws SignatureException  if verification fails.
     * @throws IllegalArgumentException if the signature scheme is not supported or if any of the clear or signature data is empty.
     */
    fun verify(publicKey: PublicKey, signatureSpec: SignatureSpec, signatureData: ByteArray, clearData: ByteArray)

    /**
     * Verifies a digital signature by identifying the signature scheme used from the input public key's type.
     * It returns true if it succeeds and false if not. In comparison to [verify] if the key and signature
     * do not match it returns false rather than throwing an exception. Normally you should use the function which throws,
     * as it avoids the risk of failing to test the result.
     * Use this method if the signature scheme is not a-priori known.
     * @param publicKey the signer's [PublicKey].
     * @param signatureData the signatureData on a message.
     * @param clearData the clear data/message that was signed (usually the Merkle root).
     * @return true if verification passes or false if verification fails.
     */
    fun isValid(publicKey: PublicKey, signatureData: ByteArray, clearData: ByteArray): Boolean

    /**
     * Verifies a digital signature by using [signatureSpec].
     * It returns true if it succeeds and false if not. In comparison to [verify] if the key and signature
     * do not match it returns false rather than throwing an exception. Normally you should use the function which throws,
     * as it avoids the risk of failing to test the result.
     * Use this method if the signature scheme is not a-priori known.
     * @param publicKey the signer's [PublicKey].
     * @param signatureData the signatureData on a message.
     * @param signatureSpec the signature spec.
     * @param clearData the clear data/message that was signed (usually the Merkle root).
     * @return true if verification passes or false if verification fails.
     */
    fun isValid(publicKey: PublicKey, signatureSpec: SignatureSpec, signatureData: ByteArray, clearData: ByteArray): Boolean
}