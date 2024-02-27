package net.corda.sdk.packaging

import net.corda.crypto.cipher.suite.SignatureSpecs
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.crypto.util.PrivateKeyFactory
import org.bouncycastle.operator.DefaultDigestAlgorithmIdentifierFinder
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder
import org.bouncycastle.operator.bc.BcRSAContentSignerBuilder
import java.io.File
import java.io.InputStream
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.util.*

class KeyStoreHelper {

    companion object {
        const val KEYSTORE_INSTANCE_TYPE = "pkcs12"
    }

    fun generateKeyStore(
        keyStoreFile: File,
        alias: String,
        password: String,
        x500Name: String = "CN=Default Signing Key, O=R3, L=London, c=GB"
    ) {
        val keyPair = KeyPairGenerator.getInstance("RSA").genKeyPair()
        val sigAlgId = DefaultSignatureAlgorithmIdentifierFinder().find(
            SignatureSpecs.RSA_SHA256.signatureName,
        )
        val digAlgId = DefaultDigestAlgorithmIdentifierFinder().find(sigAlgId)
        val parameter = PrivateKeyFactory.createKey(keyPair.private.encoded)
        val sigGen = BcRSAContentSignerBuilder(sigAlgId, digAlgId).build(parameter)
        val now = System.currentTimeMillis()
        val startDate = Date(now)
        val dnName = X500Name(x500Name)
        val certSerialNumber = BigInteger.TEN
        val endDate = Date(now + 100L * 60 * 60 * 24 * 1000)
        val certificateBuilder =
            JcaX509v3CertificateBuilder(dnName, certSerialNumber, startDate, endDate, dnName, keyPair.public)
        val certificate = JcaX509CertificateConverter().getCertificate(
            certificateBuilder.build(sigGen),
        )
        val keyStore = KeyStore.getInstance(KEYSTORE_INSTANCE_TYPE)
        keyStore.load(null, password.toCharArray())
        keyStore.setKeyEntry(
            alias,
            keyPair.private,
            password.toCharArray(),
            arrayOf(certificate),
        )
        keyStoreFile.outputStream().use {
            keyStore.store(it, password.toCharArray())
        }
    }

    fun importCertificateIntoKeyStore(
        keyStoreFile: File,
        keyStorePassword: String,
        certificateInputStream: InputStream,
        certificateAlias: String,
        certificateFactoryType: String = "X.509"
    ) {
        val keyStore = KeyStore.getInstance(KEYSTORE_INSTANCE_TYPE)
        keyStore.load(keyStoreFile.inputStream(), keyStorePassword.toCharArray())
        keyStore.setCertificateEntry(
            certificateAlias,
            CertificateFactory.getInstance(certificateFactoryType)
                .generateCertificate(certificateInputStream),
        )
        keyStoreFile.outputStream().use {
            keyStore.store(it, keyStorePassword.toCharArray())
        }
    }

    fun getDefaultGradleCertificateStream(): InputStream {
        return this::class.java.getResourceAsStream("/network/certificates/gradle-plugin-default-key.pem")!!
    }
}
