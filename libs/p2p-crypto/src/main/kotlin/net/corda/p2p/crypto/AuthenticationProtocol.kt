package net.corda.p2p.crypto

import net.corda.p2p.crypto.data.ClientHelloMessage
import net.corda.p2p.crypto.data.ServerHelloMessage
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.lang.RuntimeException
import java.nio.ByteBuffer
import java.security.*
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * A base, abstract class containing the core utilities for the session authentication protocol.
 * [AuthenticationProtocolInitiator] implements the APIs for the initiator side.
 * [AuthenticationProtocolResponder] implements the APIs for the responder side.
 *
 * For the detailed spec of the authentication protocol, refer to the corresponding design document.
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
    protected val keyPairGenerator = KeyPairGenerator.getInstance(ecAlgoName, provider).apply {
        this.initialize(256, secureRandom)
    }
    protected val keyAgreement = KeyAgreement.getInstance(ecAlgoName, provider)
    protected val hmac = Mac.getInstance("HMac-SHA256", provider)
    protected val aesCipher = Cipher.getInstance("AES/GCM/NoPadding", provider)
    protected val signature = Signature.getInstance("ECDSA", provider)
    protected val sha256Hash = SHA256Digest()

    fun generateHandshakeSecrets(inputKeyMaterial: ByteArray, clientHelloToServerHello: ByteArray): SharedHandshakeSecrets {
        val initiatorEncryptionKeyBytes = hkdf(clientHelloToServerHello, inputKeyMaterial, "Corda client hs enc key", 32)
        val initiatorEncryptionKey = SecretKeySpec(initiatorEncryptionKeyBytes, "HmacSHA512")

        val responderEncryptionKeyBytes = hkdf(clientHelloToServerHello, inputKeyMaterial, "Corda server hs enc key", 32)
        val responderEncryptionKey = SecretKeySpec(responderEncryptionKeyBytes, "HmacSHA512")

        val initiatorNonce = hkdf(clientHelloToServerHello, inputKeyMaterial, "Corda client hs enc iv", 12)
        val responderNonce = hkdf(clientHelloToServerHello, inputKeyMaterial,"Corda server hs enc iv", 12)

        val initiatorMacKeyBytes = hkdf(clientHelloToServerHello, inputKeyMaterial, "Corda client hs mac key", 32)
        val initiatorMacKey = SecretKeySpec(initiatorMacKeyBytes, "HmacSHA512")

        val responderMackKeyBytes = hkdf(clientHelloToServerHello, inputKeyMaterial, "Corda server hs mac key", 32)
        val responderMacKey = SecretKeySpec(responderMackKeyBytes, "HmacSHA512")

        return SharedHandshakeSecrets(initiatorMacKey, responderMacKey, initiatorEncryptionKey, responderEncryptionKey, initiatorNonce, responderNonce)
    }

    fun generateSessionSecrets(inputKeyMaterial: ByteArray, clientHelloToServerFinished: ByteArray): SharedSessionSecrets {
        val initiatorEncryptionKeyBytes = hkdf(clientHelloToServerFinished, inputKeyMaterial, "Corda client session key", 32)
        val initiatorEncryptionKey = SecretKeySpec(initiatorEncryptionKeyBytes, "AES")

        val responderEncryptionKeyBytes = hkdf(clientHelloToServerFinished, inputKeyMaterial, "Corda server session key", 12)
        val responderEncryptionKey = SecretKeySpec(responderEncryptionKeyBytes, "AES")

        val initiatorNonce = hkdf(clientHelloToServerFinished, inputKeyMaterial, "Corda client session iv", 12)
        val responderNonce = hkdf(clientHelloToServerFinished, inputKeyMaterial, "Corda server session iv", 12)

        return SharedSessionSecrets(initiatorEncryptionKey, responderEncryptionKey, initiatorNonce, responderNonce)
    }

    private fun hkdf(salt: ByteArray, inputKeyMaterial: ByteArray, info: String, length: Int): ByteArray {
        val hkdf = HKDFBytesGenerator(sha256Hash)
        hkdf.init(HKDFParameters(inputKeyMaterial, salt, info.toByteArray(Charsets.UTF_8)))

        val outputKeyMaterial = ByteArray(length)
        hkdf.generateBytes(outputKeyMaterial, 0, length)

        return outputKeyMaterial
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
                                      val responderNonce: ByteArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as SharedHandshakeSecrets

            if (initiatorAuthKey != other.initiatorAuthKey) return false
            if (responderAuthKey != other.responderAuthKey) return false
            if (initiatorEncryptionKey != other.initiatorEncryptionKey) return false
            if (responderEncryptionKey != other.responderEncryptionKey) return false
            if (!initiatorNonce.contentEquals(other.initiatorNonce)) return false
            if (!responderNonce.contentEquals(other.responderNonce)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = initiatorAuthKey.hashCode()
            result = 31 * result + responderAuthKey.hashCode()
            result = 31 * result + initiatorEncryptionKey.hashCode()
            result = 31 * result + responderEncryptionKey.hashCode()
            result = 31 * result + initiatorNonce.contentHashCode()
            result = 31 * result + responderNonce.contentHashCode()
            return result
        }
    }

    /**
     * @property initiatorEncryptionKey used for authentication encryption on session messages by us.
     * @property responderEncryptionKey used for authenticated encryption on session messages by peer.
     */
    data class SharedSessionSecrets(val initiatorEncryptionKey: SecretKey,
                                    val responderEncryptionKey: SecretKey,
                                    val initiatorNonce: ByteArray,
                                    val responderNonce: ByteArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as SharedSessionSecrets

            if (initiatorEncryptionKey != other.initiatorEncryptionKey) return false
            if (responderEncryptionKey != other.responderEncryptionKey) return false
            if (!initiatorNonce.contentEquals(other.initiatorNonce)) return false
            if (!responderNonce.contentEquals(other.responderNonce)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = initiatorEncryptionKey.hashCode()
            result = 31 * result + responderEncryptionKey.hashCode()
            result = 31 * result + initiatorNonce.contentHashCode()
            result = 31 * result + responderNonce.contentHashCode()
            return result
        }
    }

}

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

class InvalidHandshakeMessage: RuntimeException()

fun Int.toByteArray(): ByteArray = ByteBuffer.allocate(Int.SIZE_BYTES).putInt(this).array()