package net.corda.crypto.test.certificates.generation

import net.corda.crypto.cipher.suite.SignatureSpecs
import net.corda.crypto.test.certificates.generation.CertificateAuthority.Companion.PASSWORD
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
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.ContentSigner
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import java.math.BigInteger
import java.security.InvalidParameterException
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.X509EncodedKeySpec
import java.time.Duration
import java.util.Date
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

internal open class LocalCertificatesAuthority(
    private val keysFactoryDefinitions: KeysFactoryDefinitions,
    private val validDuration: Duration,
    savedData: SavedData?,
    issuerName: String?,
    private val parentCa: LocalCertificatesAuthority? = null
) : CertificateAuthority {

    protected val serialNumber = AtomicLong(savedData?.firstSerialNumber ?: 1)
    private val now = System.currentTimeMillis()
    internal val issuer = X500Name(issuerName ?: "C=UK, CN=r3.com, OU=${UUID.randomUUID()}")

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
        savedData?.privateKeyAndCertificate ?: generatePrivateKeyAndCertificate()
    }

    private fun generatePrivateKeyAndCertificate(): PrivateKeyWithCertificate {
        val caKeyPair = generateKeyPair()
        val (myParentCa, signerPrivateKey) = if (parentCa == null) {
            this to caKeyPair.private
        } else {
            parentCa to parentCa.privateKeyAndCertificate.privateKey
        }
        val certBuilder = myParentCa.certificateBuilder(issuer.toString(), caKeyPair.public)

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

        val signer = signerPrivateKey.signer()

        val certificate = JcaX509CertificateConverter().getCertificate(
            certBuilder.build(signer)
        )
        return PrivateKeyWithCertificate(caKeyPair.private, listOf(certificate))
    }

    private fun PrivateKey.signer(): ContentSigner {
        val signatureAlgorithm =
            when (this.algorithm) {
                "RSA" -> SignatureSpecs.RSA_SHA256.signatureName
                "EC" -> SignatureSpecs.ECDSA_SHA256.signatureName
                else -> throw InvalidParameterException("Unsupported Algorithm")
            }
        return JcaContentSignerBuilder(signatureAlgorithm).build(this)
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
                privateKeyAndCertificate.certificates.toTypedArray(),
            )
        }
    }

    override val caCertificate: Certificate by lazy {
        parentCa?.caCertificate ?: privateKeyAndCertificate.certificates.first()
    }

    override fun generateKeyAndCertificates(hosts: Collection<String>): PrivateKeyWithCertificate {
        val keys = generateKeyPair()
        val certificates = generateCertificates(hosts, keys.public)
        return PrivateKeyWithCertificate(keys.private, certificates)
    }

    override fun generateCertificates(hosts: Collection<String>, publicKey: PublicKey): Collection<Certificate> {
        val certificateBuilder = certificateBuilder(
            "C=UK, CN=${hosts.first()}",
            publicKey
        )
        hosts.forEach { host ->
            val altName = GeneralName(GeneralName.dNSName, host)
            val subjectAltName = GeneralNames(altName)
            certificateBuilder.addExtension(Extension.subjectAlternativeName, true, subjectAltName)
        }

        return listOf(
            JcaX509CertificateConverter().getCertificate(
                certificateBuilder.build(privateKeyAndCertificate.privateKey.signer())
            )
        ) + getIntermediateChain()
    }

    @Suppress("ThrowsCount", "ComplexMethod")
    override fun signCsr(csr: PKCS10CertificationRequest): Collection<Certificate> {
        val verifier = JcaContentVerifierProviderBuilder()
            .setProvider(BouncyCastleProvider())
            .build(csr.subjectPublicKeyInfo)
        if (!csr.isSignatureValid(verifier)) {
            throw CertificateAuthorityException("Invalid signature")
        }

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

        val holder = certificateGenerator.build(privateKeyAndCertificate.privateKey.signer())
        val structure = holder.toASN1Structure()
        return listOf(
            CertificateFactory.getInstance("X.509").generateCertificate(structure.encoded.inputStream())
        ) + getIntermediateChain()

    }

    private fun getIntermediateChain() : Collection<Certificate> {
        return if (parentCa == null) {
            return emptyList()
        } else {
            privateKeyAndCertificate.certificates.take(1) + parentCa.getIntermediateChain()
        }
    }

    override fun createIntermediateCertificateAuthority() : CertificateAuthority {
        return LocalCertificatesAuthority(
            keysFactoryDefinitions = keysFactoryDefinitions,
            validDuration = validDuration,
            savedData = null,
            issuerName = null,
            parentCa = this,
        )
    }
}
