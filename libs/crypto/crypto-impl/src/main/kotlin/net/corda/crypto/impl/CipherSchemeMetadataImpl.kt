package net.corda.crypto.impl

import net.corda.v5.cipher.suite.CipherSchemeMetadata
import net.corda.v5.cipher.suite.schemes.DigestScheme
import net.corda.v5.cipher.suite.schemes.SignatureScheme
import net.corda.v5.crypto.CompositeKey
import net.corda.v5.crypto.exceptions.CryptoServiceLibraryException
import net.i2p.crypto.eddsa.EdDSAPrivateKey
import net.i2p.crypto.eddsa.EdDSAPublicKey
import org.bouncycastle.asn1.DERNull
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey
import org.bouncycastle.jcajce.provider.asymmetric.edec.BCEdDSAPrivateKey
import org.bouncycastle.jcajce.provider.asymmetric.edec.BCEdDSAPublicKey
import org.bouncycastle.jcajce.provider.asymmetric.rsa.BCRSAPublicKey
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.bouncycastle.pqc.jcajce.provider.sphincs.BCSphincs256PublicKey
import org.bouncycastle.util.io.pem.PemReader
import java.io.StringReader
import java.io.StringWriter
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Provider
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec

@Suppress("LongParameterList")
open class CipherSchemeMetadataImpl(
    final override val providers: Map<String, Provider>,
    final override val schemes: Array<SignatureScheme>,
    final override val digests: Array<DigestScheme>,
    final override val secureRandom: SecureRandom,
    private val keyFactories: KeyFactoryProvider,
    private val algorithmMap: Map<AlgorithmIdentifier, SignatureScheme>
) : CipherSchemeMetadata {

    companion object {
        fun convertIfBCEdDSAPublicKey(key: PublicKey): PublicKey =
            when (key) {
                is BCEdDSAPublicKey -> EdDSAPublicKey(X509EncodedKeySpec(key.encoded))
                else -> key
            }

        fun convertIfBCEdDSAPrivateKey(key: PrivateKey): PrivateKey =
            when (key) {
                is BCEdDSAPrivateKey -> EdDSAPrivateKey(PKCS8EncodedKeySpec(key.encoded))
                else -> key
            }
    }

    override fun findSignatureScheme(algorithm: AlgorithmIdentifier): SignatureScheme =
        algorithmMap[normaliseAlgorithmIdentifier(algorithm)]
            ?: throw IllegalArgumentException("Unrecognised algorithm: ${algorithm.algorithm.id}")

    override fun findSignatureScheme(key: PublicKey): SignatureScheme {
        val keyInfo = SubjectPublicKeyInfo.getInstance(key.encoded)
        return findSignatureScheme(keyInfo.algorithm)
    }

    override fun findSignatureScheme(codeName: String): SignatureScheme = schemes.firstOrNull { it.codeName == codeName }
        ?: throw IllegalArgumentException("Unrecognised scheme code name: $codeName")

    override fun findKeyFactory(scheme: SignatureScheme): KeyFactory = keyFactories[scheme]

    override fun decodePublicKey(encodedKey: ByteArray): PublicKey = try {
        val subjectPublicKeyInfo = SubjectPublicKeyInfo.getInstance(encodedKey)
        val scheme = findSignatureScheme(subjectPublicKeyInfo.algorithm)
        val keyFactory = keyFactories[scheme]
        convertIfBCEdDSAPublicKey(keyFactory.generatePublic(X509EncodedKeySpec(encodedKey)))
    } catch (e: CryptoServiceLibraryException) {
        throw e
    } catch (e: Throwable) {
        throw CryptoServiceLibraryException("Failed to decode public key", e)
    }

    @Suppress("TooGenericExceptionCaught", "ComplexMethod")
    override fun decodePublicKey(encodedKey: String): PublicKey = try {
        val pemContent = parsePemContent(encodedKey)
        val publicKeyInfo = SubjectPublicKeyInfo.getInstance(pemContent)
        val converter = getJcaPEMKeyConverter(publicKeyInfo)
        val publicKey = converter.getPublicKey(publicKeyInfo)
        toSupportedPublicKey(publicKey)
    } catch (e: CryptoServiceLibraryException) {
        throw e
    } catch (e: Throwable) {
        throw CryptoServiceLibraryException("Failed to decode public key", e)
    }

    override fun encodeAsString(publicKey: PublicKey): String = try {
        objectToPem(publicKey)
    } catch (e: CryptoServiceLibraryException) {
        throw e
    } catch (e: Throwable) {
        throw CryptoServiceLibraryException("Failed to encode public key", e)
    }

    override fun toSupportedPublicKey(key: PublicKey): PublicKey {
        return when (key) {
            is BCECPublicKey -> key
            is BCRSAPublicKey -> key
            is BCSphincs256PublicKey -> key
            is EdDSAPublicKey -> key
            is CompositeKey -> key
            is BCEdDSAPublicKey -> convertIfBCEdDSAPublicKey(key)
            else -> decodePublicKey(key.encoded)
        }
    }

    override fun toSupportedPrivateKey(key: PrivateKey): PrivateKey {
        return convertIfBCEdDSAPrivateKey(key)
    }

    private fun getJcaPEMKeyConverter(publicKeyInfo: SubjectPublicKeyInfo): JcaPEMKeyConverter {
        val scheme = findSignatureScheme(publicKeyInfo.algorithm)
        val converter = JcaPEMKeyConverter()
        converter.setProvider(providers[scheme.providerName])
        return converter
    }

    private fun objectToPem(obj: Any): String =
        StringWriter().use { strWriter ->
            JcaPEMWriter(strWriter).use { pemWriter ->
                pemWriter.writeObject(obj)
            }
            return strWriter.toString()
        }

    private fun parsePemContent(pem: String): ByteArray =
        StringReader(pem).use { strReader ->
            return PemReader(strReader).use { pemReader ->
                pemReader.readPemObject().content
            }
        }

    private fun normaliseAlgorithmIdentifier(id: AlgorithmIdentifier): AlgorithmIdentifier =
        if (id.parameters is DERNull) {
            AlgorithmIdentifier(id.algorithm, null)
        } else {
            id
        }
}
