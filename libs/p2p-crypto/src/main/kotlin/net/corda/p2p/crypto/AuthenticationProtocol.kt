package net.corda.p2p.crypto

import net.corda.p2p.crypto.data.ClientHelloMessage
import net.corda.p2p.crypto.data.ServerHelloMessage
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.lang.RuntimeException
import java.nio.ByteBuffer
import java.security.*
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * A base, abstract class containing the core utilities for the session authentication protocol.
 * [AuthenticationProtocolInitiator] implements the APIs for the initiator side.
 * [AuthenticationProtocolResponder] implements the APIs for the responder side.
 */
abstract class AuthenticationProtocol {
    companion object {
        val clientSigPad = " ".repeat(64) + "Corda, client signature verify" + "\\0"
        val serverSigPad = " ".repeat(64) + "Corda, server signature verify" + "\\0"

        const val PROTOCOL_VERSION = 1
    }

    protected var myPrivateDHKey: PrivateKey? = null
    protected var myPublicDHKey: ByteArray? = null
    protected var peerPublicDHKey: PublicKey? = null
    protected var sharedDHSecret: ByteArray? = null
    protected var sharedHandshakeSecrets: SharedHandshakeSecrets? = null

    protected var clientHelloMessage: ClientHelloMessage? = null
    protected var serverHelloMessage: ServerHelloMessage? = null
    protected var clientHelloToServerHelloBytes: ByteArray? = null
    protected var clientHandshakePayload: ByteArray? = null
    protected var serverHandshakePayload: ByteArray? = null

    protected val secureRandom = SecureRandom()
    protected val ecAlgoName = "X25519"
    protected val provider = BouncyCastleProvider()
    protected val ephemeralKeyFactory = KeyFactory.getInstance(ecAlgoName, provider)
    protected val stableKeyFactory = KeyFactory.getInstance("EC", provider)
    protected val keyPairGenerator = KeyPairGenerator.getInstance(ecAlgoName, provider).apply {
        this.initialize(256, secureRandom)
    }
    protected val keyAgreement = KeyAgreement.getInstance(ecAlgoName, provider)
    protected val hmac = Mac.getInstance("HMac-SHA256", provider)
    protected val aesCipher = Cipher.getInstance("AES/GCM/NoPadding", provider)
    protected val signature = Signature.getInstance("ECDSA", provider)
    protected val sha256Hash = SHA256Digest()
    private val hkdf = HKDF()

    fun generateHandshakeSecrets(inputKeyMaterial: ByteArray, clientHelloToServerHello: ByteArray): SharedHandshakeSecrets {
        val zeroBytes = ByteArray(sha256Hash.digestSize) { 0 }
        val earlySecret = hkdf.extract(zeroBytes, zeroBytes)
        val salt0 = hkdfExpandLabel(earlySecret, "derived", sha256Hash.hash("".toByteArray()), 32)
        val handshakeSecret = hkdf.extract(salt0, inputKeyMaterial)

        val clientHandshakeTrafficSecret = hkdfExpandLabel(handshakeSecret, "c hs traffic", sha256Hash.hash(clientHelloToServerHello), 32)
        val serverHandshakeTrafficSecret = hkdfExpandLabel(handshakeSecret, "s hs traffic", sha256Hash.hash(clientHelloToServerHello), 32)

        val initiatorMacKeyBytes = hkdfExpandLabel(clientHandshakeTrafficSecret, "finished", ByteArray(0),32)
        val initiatorMacKey = SecretKeySpec(initiatorMacKeyBytes, "HmacSHA512")

        val responderMackKeyBytes = hkdfExpandLabel(serverHandshakeTrafficSecret, "finished", ByteArray(0), 32)
        val responderMacKey = SecretKeySpec(responderMackKeyBytes, "HmacSHA512")

        val initiatorEncryptionKeyBytes = hkdfExpandLabel(clientHandshakeTrafficSecret, "key", ByteArray(0), 16)
        val initiatorEncryptionKey = SecretKeySpec(initiatorEncryptionKeyBytes, "HmacSHA512")

        val responderEncryptionKeyBytes = hkdfExpandLabel(serverHandshakeTrafficSecret, "key", ByteArray(0), 16)
        val responderEncryptionKey = SecretKeySpec(responderEncryptionKeyBytes, "HmacSHA512")

        val initiatorNonce = hkdfExpandLabel(clientHandshakeTrafficSecret, "iv", ByteArray(0), 12)
        val responderNonce = hkdfExpandLabel(serverHandshakeTrafficSecret, "iv", ByteArray(0), 12)

        return SharedHandshakeSecrets(initiatorMacKey, responderMacKey, initiatorEncryptionKey, responderEncryptionKey, initiatorNonce, responderNonce)
    }

    fun generateSessionSecrets(inputKeyMaterial: ByteArray, clientHelloToServerFinished: ByteArray): SharedSessionSecrets {
        val zeroBytes = ByteArray(sha256Hash.digestSize) { 0 }
        val earlySecret = hkdf.extract(zeroBytes, zeroBytes)
        val salt0 = hkdfExpandLabel(earlySecret, "derived", sha256Hash.hash("".toByteArray()), 32)
        val handshakeSecret = hkdf.extract(salt0, inputKeyMaterial)
        val salt1 = hkdfExpandLabel(handshakeSecret, "derived", ByteArray(0), 32)
        val masterSecret = hkdf.extract(salt1, zeroBytes)

        val clientApplicationTrafficSecret = hkdfExpandLabel(masterSecret, "c ap traffic", sha256Hash.hash(clientHelloToServerFinished), 32)
        val serverApplicationTrafficSecret = hkdfExpandLabel(masterSecret, "s ap traffic", sha256Hash.hash(clientHelloToServerFinished), 32)

        val initiatorEncryptionKeyBytes = hkdfExpandLabel(clientApplicationTrafficSecret, "key", sha256Hash.hash(clientHelloToServerFinished), 16)
        val initiatorEncryptionKey = SecretKeySpec(initiatorEncryptionKeyBytes, "AES")

        val responderEncryptionKeyBytes = hkdfExpandLabel(serverApplicationTrafficSecret, "key", sha256Hash.hash(clientHelloToServerFinished), 16)
        val responderEncryptionKey = SecretKeySpec(responderEncryptionKeyBytes, "AES")

        val initiatorNonce = hkdfExpandLabel(clientApplicationTrafficSecret, "iv", ByteArray(0), 12)
        val responderNonce = hkdfExpandLabel(serverApplicationTrafficSecret, "iv", ByteArray(0), 12)

        return SharedSessionSecrets(initiatorEncryptionKey, responderEncryptionKey, initiatorNonce, responderNonce)
    }

    private fun hkdfExpandLabel(secret: ByteArray, label: String, context: ByteArray, length: Int): ByteArray {
        val info = (length.toString() + "tls13" + label).toByteArray() + context
        return hkdf.expand(secret, info, length)
    }


    /**
     * @property initiatorAuthKey used for MAC on handshake messages by initiator.
     * @property responderAuthKey used for MAC on handshake messages by responder.
     * @property initiatorEncryptionKey used for authenticated encryption on handshake messages by initiator.
     * @property responderEncryptionKey used for authenticated encryption on handshake messages by responder.
     *
     */
    data class SharedHandshakeSecrets(val initiatorAuthKey: SecretKey,
                                      val responderAuthKey: SecretKey,
                                      val initiatorEncryptionKey: SecretKey,
                                      val responderEncryptionKey: SecretKey,
                                      val initiatorNonce: ByteArray,
                                      val responderNonce: ByteArray)

    /**
     * @property initiatorEncryptionKey used for authentication encryption on session messages by us.
     * @property responderEncryptionKey used for authenticated encryption on session messages by peer.
     */
    data class SharedSessionSecrets(val initiatorEncryptionKey: SecretKey, val responderEncryptionKey: SecretKey, val initiatorNonce: ByteArray, val responderNonce: ByteArray)

}


fun SHA256Digest.hash(data: ByteArray): ByteArray {
    this.reset()
    this.update(data, 0, data.size)
    val hash = ByteArray(this.digestSize)
    this.doFinal(hash, 0)
    return hash
}

fun Mac.calculateMac(key: SecretKey, data: ByteArray): ByteArray {
    this.init(key)
    this.update(data)
    return this.doFinal()
}

/**
 * @return  (in this order) the encrypted data and the authentication tag
 */
fun Cipher.encryptWithAssociatedData(aad: ByteArray, nonce: ByteArray, plaintext: ByteArray, secretKey: SecretKey): Pair<ByteArray, ByteArray> {
    this.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, nonce))
    this.updateAAD(aad)
    val cipherWithTag = this.doFinal(plaintext)
    val cipher = cipherWithTag.copyOfRange(0, cipherWithTag.size - nonce.size)
    val tag = cipherWithTag.copyOfRange(cipherWithTag.size - nonce.size, cipherWithTag.size)
    return Pair(cipher, tag)
}

/**
 * @return the decrypted data
 */
fun Cipher.decrypt(aad: ByteArray, tag: ByteArray, nonce: ByteArray, ciphertext: ByteArray, secretKey: SecretKey): ByteArray {
    this.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, nonce))
    this.updateAAD(aad)
    return this.doFinal(ciphertext + tag)
}

fun Signature.verify(publicKey: PublicKey, data: ByteArray, signature: ByteArray): Boolean {
    this.initVerify(publicKey)
    this.update(data)
    return this.verify(signature)
}

/**
 * @return the shared secret key as a byte array.
 */
fun KeyAgreement.perform(privateKey: PrivateKey, publicKey: PublicKey): ByteArray  {
    this.init(privateKey)
    this.doPhase(publicKey, true)
    return this.generateSecret()
}

fun Int.toByteArray() = ByteBuffer.allocate(Int.SIZE_BYTES).putInt(this).array()

enum class MessageType {
    /**
     * Step 1 of session authentication protocol.
     */
    CLIENT_HELLO,

    /**
     * Step 2 of session authentication protocol.
     */
    SERVER_HELLO,
    /**
     * Step 3 of session authentication protocol.
     */
    CLIENT_HANDSHAKE,
    /**
     * Step 4 of session authentication protocol.
     */
    SERVER_HANDSHAKE,
    /**
     * Any data message exchanged after the session authentication protocol has been completed.
     */
    DATA
}
enum class Mode {
    AUTHENTICATION_ONLY
}

class HandshakeMacInvalid: RuntimeException()
class HandshakeSignatureInvalid: RuntimeException()