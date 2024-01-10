package net.corda.p2p.crypto.protocol.api

import net.corda.crypto.utils.PemCertificate
import net.corda.data.p2p.crypto.InitiatorHelloMessage
import net.corda.data.p2p.crypto.ProtocolMode
import net.corda.data.p2p.crypto.ResponderHelloMessage
import net.corda.data.p2p.crypto.protocol.AuthenticationProtocolCommonDetails
import net.corda.data.p2p.crypto.protocol.RevocationCheckMode
import net.corda.data.p2p.gateway.certificates.RevocationCheckRequest
import net.corda.data.p2p.gateway.certificates.RevocationCheckResponse
import net.corda.p2p.crypto.protocol.ProtocolConstants.Companion.CIPHER_ALGO
import net.corda.p2p.crypto.protocol.ProtocolConstants.Companion.CIPHER_KEY_SIZE_BYTES
import net.corda.p2p.crypto.protocol.ProtocolConstants.Companion.CIPHER_NONCE_SIZE_BYTES
import net.corda.p2p.crypto.protocol.ProtocolConstants.Companion.ELLIPTIC_CURVE_ALGO
import net.corda.p2p.crypto.protocol.ProtocolConstants.Companion.ELLIPTIC_CURVE_KEY_SIZE_BITS
import net.corda.p2p.crypto.protocol.ProtocolConstants.Companion.HASH_ALGO
import net.corda.p2p.crypto.protocol.ProtocolConstants.Companion.HMAC_ALGO
import net.corda.p2p.crypto.protocol.ProtocolConstants.Companion.HMAC_KEY_SIZE_BYTES
import net.corda.p2p.crypto.protocol.ProtocolConstants.Companion.INITIATOR_HANDSHAKE_ENCRYPTION_KEY_INFO
import net.corda.p2p.crypto.protocol.ProtocolConstants.Companion.INITIATOR_HANDSHAKE_ENCRYPTION_NONCE_INFO
import net.corda.p2p.crypto.protocol.ProtocolConstants.Companion.INITIATOR_HANDSHAKE_MAC_KEY_INFO
import net.corda.p2p.crypto.protocol.ProtocolConstants.Companion.INITIATOR_SESSION_ENCRYPTION_KEY_INFO
import net.corda.p2p.crypto.protocol.ProtocolConstants.Companion.INITIATOR_SESSION_NONCE_INFO
import net.corda.p2p.crypto.protocol.ProtocolConstants.Companion.RESPONDER_HANDSHAKE_ENCRYPTION_KEY_INFO
import net.corda.p2p.crypto.protocol.ProtocolConstants.Companion.RESPONDER_HANDSHAKE_ENCRYPTION_NONCE_INFO
import net.corda.p2p.crypto.protocol.ProtocolConstants.Companion.RESPONDER_HANDSHAKE_MAC_KEY_INFO
import net.corda.p2p.crypto.protocol.ProtocolConstants.Companion.RESPONDER_SESSION_ENCRYPTION_KEY_INFO
import net.corda.p2p.crypto.protocol.ProtocolConstants.Companion.RESPONDER_SESSION_NONCE_INFO
import net.corda.p2p.crypto.protocol.api.AuthenticationProtocol.SharedHandshakeSecrets.Companion.toCorda
import net.corda.p2p.crypto.protocol.api.Session.Companion.toAvro
import net.corda.p2p.crypto.protocol.api.Session.Companion.toSecretKey
import net.corda.p2p.crypto.util.convertToBCDigest
import net.corda.p2p.crypto.util.generateKey
import net.corda.p2p.crypto.util.hash
import net.corda.utilities.crypto.privateKeyFactory
import net.corda.utilities.crypto.publicKeyFactory
import net.corda.utilities.crypto.toPem
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SignatureSpec
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.nio.ByteBuffer
import java.security.Key
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import net.corda.data.p2p.crypto.protocol.SharedHandshakeSecrets as AvroSharedHandshakeSecrets

typealias CheckRevocation = (RevocationCheckRequest) -> RevocationCheckResponse
typealias CertificateValidatorFactory = (
    revocationCheckMode: RevocationCheckMode,
    pemTrustStore: List<PemCertificate>,
    checkRevocation: CheckRevocation,
) -> CertificateValidator

/**
 * A base, abstract class containing the core utilities for the session authentication protocol.
 * [AuthenticationProtocolInitiator] implements the APIs for the initiator side.
 * [AuthenticationProtocolResponder] implements the APIs for the responder side.
 *
 * For the detailed spec of the authentication protocol, refer to the corresponding design document.
 */
sealed class AuthenticationProtocol(private val certificateValidatorFactory: CertificateValidatorFactory) {
    companion object {
        internal val secureRandom = SecureRandom()
    }
    protected var myPrivateDHKey: PrivateKey? = null
    protected var myPublicDHKey: ByteArray? = null
    protected var peerPublicDHKey: PublicKey? = null
    protected var sharedDHSecret: ByteArray? = null
    protected var selectedMode: ProtocolMode? = null
    protected var sharedHandshakeSecrets: SharedHandshakeSecrets? = null

    protected var initiatorHelloMessage: InitiatorHelloMessage? = null
    protected var responderHelloMessage: ResponderHelloMessage? = null
    protected var initiatorHelloToResponderHelloBytes: ByteArray? = null
    protected var initiatorHandshakePayloadBytes: ByteArray? = null
    protected var responderHandshakePayloadBytes: ByteArray? = null
    protected var agreedMaxMessageSize: Int? = null

    protected val provider = BouncyCastleProvider.PROVIDER_NAME
    protected val ephemeralKeyFactory = KeyFactory.getInstance(ELLIPTIC_CURVE_ALGO, provider)
    protected val keyPairGenerator = KeyPairGenerator.getInstance(ELLIPTIC_CURVE_ALGO, provider).apply {
        this.initialize(ELLIPTIC_CURVE_KEY_SIZE_BITS, secureRandom)
    }
    protected val keyAgreement = KeyAgreement.getInstance(ELLIPTIC_CURVE_ALGO, provider)
    protected val hmac = Mac.getInstance(HMAC_ALGO, provider)
    protected val aesCipher = Cipher.getInstance(CIPHER_ALGO, provider)
    protected val messageDigest = MessageDigest.getInstance(HASH_ALGO, provider)

    private val hkdfGenerator = HKDFBytesGenerator(messageDigest.convertToBCDigest())

    fun getSignature(signatureSpec: SignatureSpec): Signature {
        return Signature.getInstance(signatureSpec.signatureName, provider)
    }

    fun generateHandshakeSecrets(inputKeyMaterial: ByteArray, initiatorHelloToResponderHello: ByteArray): SharedHandshakeSecrets {
        val initiatorEncryptionKeyBytes = hkdfGenerator.generateKey(
            initiatorHelloToResponderHello,
            inputKeyMaterial,
            INITIATOR_HANDSHAKE_ENCRYPTION_KEY_INFO,
            CIPHER_KEY_SIZE_BYTES,
        )
        val initiatorEncryptionKey = SecretKeySpec(initiatorEncryptionKeyBytes, CIPHER_ALGO)

        val responderEncryptionKeyBytes = hkdfGenerator.generateKey(
            initiatorHelloToResponderHello,
            inputKeyMaterial,
            RESPONDER_HANDSHAKE_ENCRYPTION_KEY_INFO,
            CIPHER_KEY_SIZE_BYTES,
        )
        val responderEncryptionKey = SecretKeySpec(responderEncryptionKeyBytes, CIPHER_ALGO)

        val initiatorNonce = hkdfGenerator.generateKey(
            initiatorHelloToResponderHello,
            inputKeyMaterial,
            INITIATOR_HANDSHAKE_ENCRYPTION_NONCE_INFO,
            CIPHER_NONCE_SIZE_BYTES,
        )
        val responderNonce = hkdfGenerator.generateKey(
            initiatorHelloToResponderHello,
            inputKeyMaterial,
            RESPONDER_HANDSHAKE_ENCRYPTION_NONCE_INFO,
            CIPHER_NONCE_SIZE_BYTES,
        )

        val initiatorMacKeyBytes = hkdfGenerator.generateKey(
            initiatorHelloToResponderHello,
            inputKeyMaterial,
            INITIATOR_HANDSHAKE_MAC_KEY_INFO,
            HMAC_KEY_SIZE_BYTES,
        )
        val initiatorMacKey = SecretKeySpec(initiatorMacKeyBytes, HMAC_ALGO)

        val responderMackKeyBytes = hkdfGenerator.generateKey(
            initiatorHelloToResponderHello,
            inputKeyMaterial,
            RESPONDER_HANDSHAKE_MAC_KEY_INFO,
            HMAC_KEY_SIZE_BYTES,
        )
        val responderMacKey = SecretKeySpec(responderMackKeyBytes, HMAC_ALGO)

        return SharedHandshakeSecrets(
            initiatorMacKey,
            responderMacKey,
            initiatorEncryptionKey,
            responderEncryptionKey,
            initiatorNonce,
            responderNonce,
        )
    }

    fun generateSessionSecrets(inputKeyMaterial: ByteArray, initiatorHelloToResponderFinished: ByteArray): SharedSessionSecrets {
        val initiatorEncryptionKeyBytes = hkdfGenerator.generateKey(
            initiatorHelloToResponderFinished,
            inputKeyMaterial,
            INITIATOR_SESSION_ENCRYPTION_KEY_INFO,
            CIPHER_KEY_SIZE_BYTES,
        )
        val initiatorEncryptionKey = SecretKeySpec(initiatorEncryptionKeyBytes, CIPHER_ALGO)

        val responderEncryptionKeyBytes = hkdfGenerator.generateKey(
            initiatorHelloToResponderFinished,
            inputKeyMaterial,
            RESPONDER_SESSION_ENCRYPTION_KEY_INFO,
            CIPHER_KEY_SIZE_BYTES,
        )
        val responderEncryptionKey = SecretKeySpec(responderEncryptionKeyBytes, CIPHER_ALGO)

        val initiatorNonce = hkdfGenerator.generateKey(
            initiatorHelloToResponderFinished,
            inputKeyMaterial,
            INITIATOR_SESSION_NONCE_INFO,
            CIPHER_NONCE_SIZE_BYTES,
        )
        val responderNonce = hkdfGenerator.generateKey(
            initiatorHelloToResponderFinished,
            inputKeyMaterial,
            RESPONDER_SESSION_NONCE_INFO,
            CIPHER_NONCE_SIZE_BYTES,
        )

        return SharedSessionSecrets(initiatorEncryptionKey, responderEncryptionKey, initiatorNonce, responderNonce)
    }

    protected fun validateCertificate(
        certificateCheckMode: CertificateCheckMode,
        peerCertificate: List<String>?,
        peerX500Name: MemberX500Name,
        expectedPeerPublicKey: PublicKey,
        messageName: String,
    ) {
        if (certificateCheckMode is CertificateCheckMode.CheckCertificate) {
            if (peerCertificate != null) {
                val certificateValidator = certificateValidatorFactory(
                    certificateCheckMode.revocationCheckMode,
                    certificateCheckMode.truststore,
                    certificateCheckMode.revocationChecker,
                )
                certificateValidator.validate(
                    peerCertificate,
                    peerX500Name,
                    expectedPeerPublicKey,
                )
            } else {
                throw InvalidPeerCertificate("No peer certificate was sent in the $messageName.")
            }
        }
    }

    /**
     * @property initiatorAuthKey used for MAC on handshake messages by initiator.
     * @property responderAuthKey used for MAC on handshake messages by responder.
     * @property initiatorEncryptionKey used for authenticated encryption on handshake messages by initiator.
     * @property responderEncryptionKey used for authenticated encryption on handshake messages by responder.
     *
     */
    data class SharedHandshakeSecrets(
        val initiatorAuthKey: SecretKey,
        val responderAuthKey: SecretKey,
        val initiatorEncryptionKey: SecretKey,
        val responderEncryptionKey: SecretKey,
        val initiatorNonce: ByteArray,
        val responderNonce: ByteArray,
    ) {
        internal companion object {
            fun AvroSharedHandshakeSecrets.toCorda() = SharedHandshakeSecrets(
                initiatorAuthKey = this.initiatorAuthKey.toSecretKey(),
                responderAuthKey = this.responderAuthKey.toSecretKey(),
                initiatorEncryptionKey = this.initiatorEncryptionKey.toSecretKey(),
                responderEncryptionKey = this.responderEncryptionKey.toSecretKey(),
                initiatorNonce = this.initiatorNonce.array(),
                responderNonce = this.responderNonce.array(),
            )
        }
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

        fun toAvro(): AvroSharedHandshakeSecrets {
            return AvroSharedHandshakeSecrets(
                initiatorAuthKey.toAvro(),
                responderAuthKey.toAvro(),
                initiatorEncryptionKey.toAvro(),
                responderEncryptionKey.toAvro(),
                ByteBuffer.wrap(initiatorNonce),
                ByteBuffer.wrap(responderNonce),
            )
        }
    }

    /**
     * @property initiatorEncryptionKey used for authentication encryption on session messages by us.
     * @property responderEncryptionKey used for authenticated encryption on session messages by peer.
     */
    data class SharedSessionSecrets(
        val initiatorEncryptionKey: SecretKey,
        val responderEncryptionKey: SecretKey,
        val initiatorNonce: ByteArray,
        val responderNonce: ByteArray,
    ) {
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

    fun hash(key: Key): ByteArray {
        return messageDigest.hash(key.encoded)
    }

    protected fun applyCommonDetails(header: AuthenticationProtocolCommonDetails) {
        myPrivateDHKey = header.myPrivateDHKey?.let {
            privateKeyFactory(it.reader())
        }
        myPublicDHKey = header.myPublicDHKey?.array()
        peerPublicDHKey = header.peerPublicDHKey?.let {
            publicKeyFactory(it.reader())
        }
        sharedDHSecret = header.sharedDHSecret?.array()
        selectedMode = header.selectedMode
        sharedHandshakeSecrets = header.sharedHandshakeSecrets?.toCorda()
        initiatorHelloMessage = header.initiatorHelloMessage
        responderHelloMessage = header.responderHelloMessage
        initiatorHelloToResponderHelloBytes = header.initiatorHelloToResponderHelloBytes?.array()
        initiatorHandshakePayloadBytes = header.initiatorHandshakePayloadBytes?.array()
        responderHandshakePayloadBytes = header.responderHandshakePayloadBytes?.array()
        agreedMaxMessageSize = header.agreedMaxMessageSize
    }

    protected fun toAvro(
        sessionId: String,
        ourMaxMessageSize: Int,
        session: Session?,
    ) =
        AuthenticationProtocolCommonDetails(
            sessionId,
            ourMaxMessageSize,
            session?.toAvro(),
            myPrivateDHKey?.toPem(),
            myPublicDHKey?.let {
                ByteBuffer.wrap(it)
            },
            peerPublicDHKey?.toPem(),
            sharedDHSecret?.let {
                ByteBuffer.wrap(it)
            },
            selectedMode,
            sharedHandshakeSecrets?.toAvro(),
            initiatorHelloMessage,
            responderHelloMessage,
            initiatorHelloToResponderHelloBytes?.let {
                ByteBuffer.wrap(it)
            },
            initiatorHandshakePayloadBytes?.let {
                ByteBuffer.wrap(initiatorHandshakePayloadBytes)
            },
            responderHandshakePayloadBytes?.let {
                ByteBuffer.wrap(responderHandshakePayloadBytes)
            },
            agreedMaxMessageSize,
        )
}

internal fun Long.toByteArray(): ByteArray = ByteBuffer.allocate(Long.SIZE_BYTES).putLong(this).array()
