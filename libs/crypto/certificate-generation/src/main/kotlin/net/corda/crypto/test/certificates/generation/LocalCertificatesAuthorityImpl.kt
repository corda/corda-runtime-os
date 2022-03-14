package net.corda.crypto.test.certificates.generation

import net.corda.v5.cipher.suite.schemes.ECDSA_SECP256K1_SHA256_SIGNATURE_SPEC
import net.corda.v5.cipher.suite.schemes.RSA_SHA256_SIGNATURE_SPEC
import net.corda.v5.cipher.suite.schemes.SignatureSchemeTemplate
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.crypto.util.PrivateKeyFactory
import org.bouncycastle.operator.DefaultDigestAlgorithmIdentifierFinder
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder
import org.bouncycastle.operator.bc.BcECContentSignerBuilder
import org.bouncycastle.operator.bc.BcRSAContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.InvalidParameterException
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PublicKey
import java.time.Duration
import java.util.Date
import java.util.concurrent.atomic.AtomicLong

internal class LocalCertificatesAuthorityImpl(
    private val signatureSchemeTemplate: SignatureSchemeTemplate,
    private val directory: File?,
    private val validDuration: Duration,
) : LocalCertificatesAuthority() {
    private val now = System.currentTimeMillis()
    private val serialNumberFile by lazy {
        directory?.let {
            File(it, "serialNumber.txt")
        }
    }

    private val keyStoreFile by lazy {
        directory?.let {
            File(it, "keyStoreFile.jks")
        }
    }

    private val serialNumber by lazy {
        if (serialNumberFile?.exists() == true) {
            AtomicLong(serialNumberFile!!.readText().toLong())
        } else {
            AtomicLong(now)
        }
    }

    private fun generateKeyPair(): KeyPair {
        val keysFactory = KeyPairGenerator.getInstance(signatureSchemeTemplate.algorithmName)
        return keysFactory.generateKeyPair()
    }

    private val certificateAndPrivateKey by lazy {
        val keyStoreFile = keyStoreFile
        if (keyStoreFile?.exists() == true) {
            val keyStore = keyStoreFile.inputStream().use { input ->
                KeyStore.getInstance("JKS").also { keyStore ->
                    keyStore.load(input, PASSWORD.toCharArray())
                }
            }
            val alias = keyStore.aliases().nextElement()
            keyStore.getCertificate(alias) to keyStore.getKey(alias, PASSWORD.toCharArray())
        } else {
            val caKeyPair = generateKeyPair()
            val certBuilder = certificateBuilder("C=UK CN=r3.com", caKeyPair.public)

            val basicConstraints = BasicConstraints(true)

            certBuilder.addExtension(
                Extension.basicConstraints,
                true,
                basicConstraints
            )
            val signatureAlgorithm = signatureSchemeTemplate.signatureSpec.signatureName
            val signer = JcaContentSignerBuilder(signatureAlgorithm).build(caKeyPair.private)

            JcaX509CertificateConverter().getCertificate(
                certBuilder.build(signer)
            ) to caKeyPair.private
        }
    }

    private fun nextSerialNumber() =
        BigInteger(serialNumber.incrementAndGet().toString())

    private fun certificateBuilder(name: String, key: PublicKey): JcaX509v3CertificateBuilder {
        val startDate = Date(now)
        val dnName = X500Name(name)
        val certSerialNumber = nextSerialNumber()
        val endDate = Date(now + validDuration.toMillis())
        return JcaX509v3CertificateBuilder(dnName, certSerialNumber, startDate, endDate, dnName, key)
    }

    override fun createAuthorityKeyStore(alias: String): KeyStore {
        return KeyStore.getInstance("JKS").also {
            it.load(null)
            it.setKeyEntry(
                alias,
                certificateAndPrivateKey.second, PASSWORD.toCharArray(),
                arrayOf(certificateAndPrivateKey.first),
            )
        }
    }

    override val caCertificate by lazy {
        certificateAndPrivateKey.first
    }

    override fun generateKeyAndCertificate(hosts: Collection<String>): PrivateKeyWithCertificate {
        val signatureAlgorithm =
            when (certificateAndPrivateKey.second.algorithm) {
                "RSA" -> RSA_SHA256_SIGNATURE_SPEC.signatureName
                "EC" -> ECDSA_SECP256K1_SHA256_SIGNATURE_SPEC.signatureName
                else -> throw InvalidParameterException("Unsupported Algorithm")
            }
        val sigAlgId = DefaultSignatureAlgorithmIdentifierFinder().find(signatureAlgorithm)
        val digAlgId = DefaultDigestAlgorithmIdentifierFinder().find(sigAlgId)
        val parameter = PrivateKeyFactory.createKey(certificateAndPrivateKey.second.encoded)
        val sigGen = when (certificateAndPrivateKey.second.algorithm) {
            "RSA" -> BcRSAContentSignerBuilder(sigAlgId, digAlgId)
            "EC" -> BcECContentSignerBuilder(sigAlgId, digAlgId)
            else -> throw InvalidParameterException("Unsupported Algorithm")
        }.build(parameter)

        val keys = generateKeyPair()
        val certificateBuilder = certificateBuilder(
            "C=UK CN=${hosts.first()}",
            keys.public
        )
        hosts.forEach { host ->
            val altName = GeneralName(GeneralName.dNSName, host)
            val subjectAltName = GeneralNames(altName)
            certificateBuilder.addExtension(Extension.subjectAlternativeName, true, subjectAltName)
        }

        val certificate = JcaX509CertificateConverter().getCertificate(
            certificateBuilder.build(sigGen)
        )

        return PrivateKeyWithCertificate(keys.private, certificate)
    }

    override fun close() {
        if (directory != null) {
            directory.mkdirs()
            serialNumberFile?.writeText(serialNumber.toString())
            keyStoreFile?.outputStream()?.use {
                createAuthorityKeyStore("alias").store(it, PASSWORD.toCharArray())
            }
        }
    }
}
