package net.corda.crypto.test.certificates.generation

import net.corda.crypto.test.certificates.generation.CertificateAuthority.Companion.PASSWORD
import net.corda.v5.crypto.SignatureSpec
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.Extensions
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.crypto.util.PrivateKeyFactory
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.DefaultDigestAlgorithmIdentifierFinder
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder
import org.bouncycastle.operator.bc.BcECContentSignerBuilder
import org.bouncycastle.operator.bc.BcRSAContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import java.math.BigInteger
import java.security.InvalidParameterException
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PublicKey
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.X509EncodedKeySpec
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

    private val issuer = X500Name("C=UK, CN=r3.com")

    private fun generatePrivateKeyAndCertificate(): PrivateKeyWithCertificate {
        val caKeyPair = generateKeyPair()
        val certBuilder = certificateBuilder(issuer.toString(), caKeyPair.public)

        val basicConstraints = BasicConstraints(true)

        certBuilder.addExtension(
            Extension.basicConstraints,
            true,
            basicConstraints
        )
        certBuilder.addExtension(
            Extension.keyUsage, false, KeyUsage(
                KeyUsage.digitalSignature
                        or KeyUsage.keyEncipherment
                        or KeyUsage.keyAgreement
                        or KeyUsage.keyCertSign
                        or KeyUsage.cRLSign
            )
        )

        val signatureAlgorithm = when (keysFactoryDefinitions.algorithm) {
            Algorithm.RSA -> SignatureSpec.RSA_SHA256
            Algorithm.EC -> SignatureSpec.ECDSA_SHA256
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
        return JcaX509v3CertificateBuilder(issuer, certSerialNumber, startDate, endDate, dnName, key)
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
        val keys = generateKeyPair()
        val certificate = generateCertificate(hosts, keys.public)
        return PrivateKeyWithCertificate(keys.private, certificate)
    }

    override fun generateClientKeyAndCertificate(subject: String): PrivateKeyWithCertificate {
        val keys = generateKeyPair()
        val certificate = generateCertificate(emptyList(), keys.public, subject)
        return PrivateKeyWithCertificate(keys.private, certificate)
    }

    override fun generateCertificate(hosts: Collection<String>, publicKey: PublicKey): Certificate {
        return generateCertificate(hosts, publicKey, "C=UK, CN=${hosts.first()}")
    }
    private fun generateCertificate(hosts: Collection<String>, publicKey: PublicKey, subject: String): Certificate {
        val signatureAlgorithm =
            when (privateKeyAndCertificate.privateKey.algorithm) {
                "RSA" -> SignatureSpec.RSA_SHA256.signatureName
                "EC" -> SignatureSpec.ECDSA_SHA256.signatureName
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

        val certificateBuilder = certificateBuilder(
            subject,
            publicKey,
        )
        hosts.forEach { host ->
            val altName = GeneralName(GeneralName.dNSName, host)
            val subjectAltName = GeneralNames(altName)
            certificateBuilder.addExtension(Extension.subjectAlternativeName, true, subjectAltName)
        }

        return JcaX509CertificateConverter().getCertificate(
            certificateBuilder.build(sigGen)
        )
    }

    @Suppress("ThrowsCount", "ComplexMethod")
    fun signCsr(csr: PKCS10CertificationRequest): Certificate {
        val verifier = JcaContentVerifierProviderBuilder()
            .setProvider(BouncyCastleProvider())
            .build(csr.subjectPublicKeyInfo)
        if (!csr.isSignatureValid(verifier)) {
            throw CertificateAuthorityException("Invalid signature")
        }

        val signatureAlgorithm =
            when (privateKeyAndCertificate.privateKey.algorithm) {
                "RSA" -> SignatureSpec.RSA_SHA256.signatureName
                "EC" -> SignatureSpec.ECDSA_SHA256.signatureName
                else -> throw InvalidParameterException("Unsupported Algorithm")
            }
        val sigAlgId = DefaultSignatureAlgorithmIdentifierFinder().find(signatureAlgorithm)
        val digAlgId = DefaultDigestAlgorithmIdentifierFinder().find(sigAlgId)
        val parameter = PrivateKeyFactory.createKey(privateKeyAndCertificate.privateKey.encoded)

        val startDate = Date(now)
        val certSerialNumber = nextSerialNumber()
        val endDate = Date(now + validDuration.toMillis())

        val keyFactory = KeyFactory.getInstance(
            csr.subjectPublicKeyInfo.algorithm.algorithm.id,
            BouncyCastleProvider()
        )
        val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(csr.subjectPublicKeyInfo.encoded))

        val certificateGenerator = JcaX509v3CertificateBuilder(
            issuer,
            certSerialNumber,
            startDate,
            endDate,
            csr.subject,
            publicKey,
        )

        csr.getAttributes(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest)?.flatMap {
            it.attrValues
        }?.filterNotNull()
            ?.mapNotNull {
                Extensions.getInstance(it)
            }?.forEach { extensions ->
                extensions.extensionOIDs.forEach { oid ->
                    val extension = extensions.getExtension(oid)
                    certificateGenerator.addExtension(extension)
                }
            }

        val sigGen = when (privateKeyAndCertificate.privateKey.algorithm) {
            "RSA" -> BcRSAContentSignerBuilder(sigAlgId, digAlgId)
            "EC" -> BcECContentSignerBuilder(sigAlgId, digAlgId)
            else -> throw InvalidParameterException("Unsupported Algorithm")
        }.build(parameter)

        val holder = certificateGenerator.build(sigGen)
        val structure = holder.toASN1Structure()
        return CertificateFactory.getInstance("X.509").generateCertificate(structure.encoded.inputStream())
    }
}
