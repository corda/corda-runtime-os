package net.corda.p2p.gateway.messaging.http

import net.corda.p2p.GatewayTruststore
import net.corda.p2p.test.KeyAlgorithm
import net.corda.v5.base.util.days
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.crypto.util.PrivateKeyFactory
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.bouncycastle.operator.DefaultDigestAlgorithmIdentifierFinder
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder
import org.bouncycastle.operator.bc.BcECContentSignerBuilder
import org.bouncycastle.operator.bc.BcRSAContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.StringWriter
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PublicKey
import java.util.Calendar
import java.util.Date
import java.util.concurrent.atomic.AtomicLong

internal class StubCertificatesAuthority(
    private val password: String,
    private val keyAlgorithm: KeyAlgorithm,
) {
    private val now = System.currentTimeMillis()
    private val serialNumber = AtomicLong(now)
    private val providerName by lazy {
        when (keyAlgorithm) {
            KeyAlgorithm.RSA -> "RSA"
            KeyAlgorithm.ECDSA -> "EC"
        }
    }
    private val signatureAlgorithm by lazy {
        when (keyAlgorithm) {
            KeyAlgorithm.RSA -> "SHA256WithRSA"
            KeyAlgorithm.ECDSA -> "SHA256withECDSA"
        }
    }
    private fun generateKeyPair(): KeyPair {
        val keysFactory = KeyPairGenerator.getInstance(providerName)
        return keysFactory.generateKeyPair()
    }

    private val caKeyPair by lazy {
        generateKeyPair()
    }

    private fun nextSerialNumber() =
        BigInteger(serialNumber.incrementAndGet().toString())

    private fun certificateBuilder(name: String, key: PublicKey): JcaX509v3CertificateBuilder {
        val startDate = Date(now)
        val dnName = X500Name(name)
        val certSerialNumber = nextSerialNumber()
        val calendar = Calendar.getInstance()
        calendar.time = startDate
        calendar.add(Calendar.YEAR, 1)
        val endDate = Date(now + 30.days.toMillis())
        return JcaX509v3CertificateBuilder(dnName, certSerialNumber, startDate, endDate, dnName, key)
    }

    private val caCertificate by lazy {

        val certBuilder = certificateBuilder("C=UK CN=r3.com", caKeyPair.public)

        val basicConstraints = BasicConstraints(true)

        certBuilder.addExtension(
            Extension.basicConstraints,
            true,
            basicConstraints
        )

        val signer = JcaContentSignerBuilder(signatureAlgorithm).build(caKeyPair.private)

        JcaX509CertificateConverter().getCertificate(
            certBuilder.build(signer)
        )
    }

    fun createKeyStore(
        host: String,
    ): KeyStoreWithPassword {
        val sigAlgId = DefaultSignatureAlgorithmIdentifierFinder().find(signatureAlgorithm)
        val digAlgId = DefaultDigestAlgorithmIdentifierFinder().find(sigAlgId)
        val parameter = PrivateKeyFactory.createKey(caKeyPair.private.encoded)
        val sigGen = when (keyAlgorithm) {
            KeyAlgorithm.RSA -> BcRSAContentSignerBuilder(sigAlgId, digAlgId)
            KeyAlgorithm.ECDSA -> BcECContentSignerBuilder(sigAlgId, digAlgId)
        }.build(parameter)

        val keys = generateKeyPair()
        val certificateBuilder = certificateBuilder(
            "C=UK CN=$host",
            keys.public
        )
        val altName = GeneralName(GeneralName.dNSName, host)
        val subjectAltName = GeneralNames(altName)
        certificateBuilder.addExtension(Extension.subjectAlternativeName, true, subjectAltName)

        val certificate = JcaX509CertificateConverter().getCertificate(
            certificateBuilder.build(sigGen)
        )

        val keyStore = KeyStore.getInstance("PKCS12").also { keyStore ->
            keyStore.load(null)
            keyStore.setKeyEntry("entry", keys.private, password.toCharArray(), arrayOf(certificate))
        }
        return KeyStoreWithPassword(keyStore, password)
    }

    val trustStoreKeyStore: KeyStore by lazy {
        KeyStore.getInstance("PKCS12").also { keyStore ->
            keyStore.load(null)
            keyStore.setCertificateEntry("alias", caCertificate)
        }
    }

    private val trustStorePem by lazy {
        StringWriter().use { str ->
            JcaPEMWriter(str).use { writer ->
                writer.writeObject(caCertificate)
            }
            str.toString()
        }
    }
    val gatewayTrustStore by lazy {
        GatewayTruststore(listOf(trustStorePem))
    }
}
