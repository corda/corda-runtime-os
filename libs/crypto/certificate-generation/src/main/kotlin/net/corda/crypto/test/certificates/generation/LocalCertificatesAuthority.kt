package net.corda.crypto.test.certificates.generation

import net.corda.crypto.test.certificates.generation.CertificateAuthority.Companion.PASSWORD
import net.corda.v5.cipher.suite.schemes.ECDSA_SECP256R1_SHA256_SIGNATURE_SPEC
import net.corda.v5.cipher.suite.schemes.RSA_SHA256_SIGNATURE_SPEC
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.crypto.util.PrivateKeyFactory
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.DefaultDigestAlgorithmIdentifierFinder
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder
import org.bouncycastle.operator.bc.BcECContentSignerBuilder
import org.bouncycastle.operator.bc.BcRSAContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.InvalidParameterException
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PublicKey
import java.time.Duration
import java.util.Date
import java.util.concurrent.atomic.AtomicLong

internal open class LocalCertificatesAuthority(
    private val keysFactoryDefinitions: KeysFactoryDefinitions,
    private val validDuration: Duration,
    private val defaultPrivateKeyAndCertificate: PrivateKeyWithCertificate?,
    firstSerialNumber: Long = 1,
) : CertificateAuthority {

    protected val serialNumber = AtomicLong(firstSerialNumber)
    private val now = System.currentTimeMillis()

    private fun generateKeyPair(): KeyPair {
        val keysFactory = KeyPairGenerator.getInstance(keysFactoryDefinitions.algorithm.name, BouncyCastleProvider())
        keysFactoryDefinitions.spec?.also { spec ->
            keysFactory.initialize(spec)
        }
        keysFactoryDefinitions.keySize?.also {
            keysFactory.initialize(it)
        }

        return keysFactory.generateKeyPair()
    }
    private val privateKeyAndCertificate by lazy {
        defaultPrivateKeyAndCertificate ?: generatePrivateKeyAndCertificate()
    }

    private fun generatePrivateKeyAndCertificate(): PrivateKeyWithCertificate {
        val caKeyPair = generateKeyPair()
        val certBuilder = certificateBuilder("C=UK CN=r3.com", caKeyPair.public)

        val basicConstraints = BasicConstraints(true)

        certBuilder.addExtension(
            Extension.basicConstraints,
            true,
            basicConstraints
        )
        val signatureAlgorithm = when (keysFactoryDefinitions.algorithm) {
            Algorithm.RSA -> RSA_SHA256_SIGNATURE_SPEC
            Algorithm.EC -> ECDSA_SECP256R1_SHA256_SIGNATURE_SPEC
        }.signatureName
        val signer = JcaContentSignerBuilder(signatureAlgorithm).build(caKeyPair.private)

        val certificate = JcaX509CertificateConverter().getCertificate(
            certBuilder.build(signer)
        )
        return PrivateKeyWithCertificate(caKeyPair.private, certificate)
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

    internal fun asKeyStore(alias: String): KeyStore {
        return KeyStore.getInstance("JKS").also {
            it.load(null)
            it.setKeyEntry(
                alias,
                privateKeyAndCertificate.privateKey, PASSWORD.toCharArray(),
                arrayOf(privateKeyAndCertificate.certificate),
            )
        }
    }

    override val caCertificate by lazy {
        privateKeyAndCertificate.certificate
    }

    override fun generateKeyAndCertificate(hosts: Collection<String>): PrivateKeyWithCertificate {
        val signatureAlgorithm =
            when (privateKeyAndCertificate.privateKey.algorithm) {
                "RSA" -> RSA_SHA256_SIGNATURE_SPEC.signatureName
                "EC" -> ECDSA_SECP256R1_SHA256_SIGNATURE_SPEC.signatureName
                else -> throw InvalidParameterException("Unsupported Algorithm")
            }
        val sigAlgId = DefaultSignatureAlgorithmIdentifierFinder().find(signatureAlgorithm)
        val digAlgId = DefaultDigestAlgorithmIdentifierFinder().find(sigAlgId)
        val parameter = PrivateKeyFactory.createKey(privateKeyAndCertificate.privateKey.encoded)
        val sigGen = when (privateKeyAndCertificate.privateKey.algorithm) {
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
}
