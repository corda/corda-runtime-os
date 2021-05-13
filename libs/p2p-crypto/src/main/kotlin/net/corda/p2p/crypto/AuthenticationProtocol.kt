package net.corda.p2p.crypto

import net.corda.p2p.crypto.data.ClientHelloMessage
import net.corda.p2p.crypto.data.ServerHelloMessage
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.jce.provider.BouncyCastleProvider
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
    protected val hash = MessageDigest.getInstance("SHA-512", provider)
    protected val aesCipher = Cipher.getInstance("AES/GCM/NoPadding", provider)
    protected val signature = Signature.getInstance("ECDSA", provider)
    private val sha256Hash = SHA256Digest()
    private val hkdf = HKDF()

    fun generateHandshakeSecrets(inputKeyMaterial: ByteArray, clientHelloToServerHello: ByteArray): SharedHandshakeSecrets {
        val earlySecret = hkdf.extract(byteArrayOf(0, 0), ByteArray(32) { 0 })
        val info = calculateInfo("derived", sha256Hash.hash("".toByteArray()), 32)
        val salt0 = hkdf.expand(earlySecret, info, 32)
        val handshakeSecret = hkdf.extract(salt0, inputKeyMaterial)


        val clientHandshakeInfo = calculateInfo("c hs traffic", sha256Hash.hash(clientHelloToServerHello), 32)
        val serverHandshakeInfo = calculateInfo("s hs traffic", sha256Hash.hash(clientHelloToServerHello), 32)
        val clientHandshakeTrafficSecret = hkdf.expand(handshakeSecret, clientHandshakeInfo, 32)
        val serverHandshakeTrafficSecret = hkdf.expand(handshakeSecret, serverHandshakeInfo, 32)
        val initiatorMacKeyBytes = hkdf.expand(clientHandshakeTrafficSecret, calculateInfo("finished", sha256Hash.hash(ByteArray(0)), 32), 32)
        val initiatorMacKey = SecretKeySpec(initiatorMacKeyBytes, "HmacSHA512")
        val responderMackKeyBytes = hkdf.expand(serverHandshakeTrafficSecret, calculateInfo("finished", sha256Hash.hash(ByteArray(0)), 32), 32)
        val responderMacKey = SecretKeySpec(responderMackKeyBytes, "HmacSHA512")
        val initiatorEncryptionKeyBytes = hkdf.expand(clientHandshakeTrafficSecret, calculateInfo("key", sha256Hash.hash(ByteArray(0)), 16), 16)
        val initiatorEncryptionKey = SecretKeySpec(initiatorEncryptionKeyBytes, "HmacSHA512")
        val responderEncryptionKeyBytes = hkdf.expand(serverHandshakeTrafficSecret, calculateInfo("key", sha256Hash.hash(ByteArray(0)), 16), 16)
        val responderEncryptionKey = SecretKeySpec(responderEncryptionKeyBytes, "HmacSHA512")
        val initiatorNonce = hkdf.expand(clientHandshakeTrafficSecret, calculateInfo("iv", sha256Hash.hash(ByteArray(0)), 12), 12)
        val responderNonce = hkdf.expand(serverHandshakeTrafficSecret, calculateInfo("iv", sha256Hash.hash(ByteArray(0)), 12), 12)

        return SharedHandshakeSecrets(initiatorMacKey, responderMacKey, initiatorEncryptionKey, responderEncryptionKey, initiatorNonce, responderNonce)
    }

    fun generateSessionSecrets(inputKeyMaterial: ByteArray, clientHelloToServerFinished: ByteArray): SharedSessionSecrets {
        val earlySecret = hkdf.extract(byteArrayOf(0, 0), ByteArray(32) { 0 })
        val info = calculateInfo("derived", sha256Hash.hash("".toByteArray()), 32)
        val salt0 = hkdf.expand(earlySecret, info, 32)
        val handshakeSecret = hkdf.extract(salt0, inputKeyMaterial)
        val salt1 = hkdf.expand(handshakeSecret, calculateInfo("derived", sha256Hash.hash(ByteArray(0)), 32), 32)
        val masterSecret = hkdf.extract(salt1, ByteArray(32) { 0 })

        val clientApplicationTrafficSecret = hkdf.expand(masterSecret, calculateInfo("c ap traffic", sha256Hash.hash(clientHelloToServerFinished), 32), 32)
        val serverApplicationTrafficSecret = hkdf.expand(masterSecret, calculateInfo("s ap traffic", sha256Hash.hash(clientHelloToServerFinished), 32), 32)
        val initiatorEncryptionKeyBytes = hkdf.expand(clientApplicationTrafficSecret, calculateInfo("key", sha256Hash.hash(clientHelloToServerFinished), 16), 16)
        val initiatorEncryptionKey = SecretKeySpec(initiatorEncryptionKeyBytes, "AES")
        val responderEncryptionKeyBytes = hkdf.expand(serverApplicationTrafficSecret, calculateInfo("key", sha256Hash.hash(clientHelloToServerFinished), 16), 16)
        val responderEncryptionKey = SecretKeySpec(responderEncryptionKeyBytes, "AES")
        val initiatorNonce = hkdf.expand(clientApplicationTrafficSecret, calculateInfo("iv", ByteArray(0), 12), 12)
        val responderNonce = hkdf.expand(serverApplicationTrafficSecret, calculateInfo("iv", ByteArray(0), 12), 12)

        return SharedSessionSecrets(initiatorEncryptionKey, responderEncryptionKey, initiatorNonce, responderNonce)
    }

    private fun calculateInfo(label: String, context: ByteArray, length: Int): ByteArray {
        return (length.toString() + "tls13" + label).toByteArray() + context
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