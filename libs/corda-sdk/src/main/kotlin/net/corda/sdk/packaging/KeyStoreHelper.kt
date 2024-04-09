package net.corda.sdk.packaging

import net.corda.crypto.cipher.suite.SignatureSpecs
import net.corda.v5.base.types.MemberX500Name
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.crypto.util.PrivateKeyFactory
import org.bouncycastle.openssl.jcajce.JcaMiscPEMGenerator
import org.bouncycastle.operator.DefaultDigestAlgorithmIdentifierFinder
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder
import org.bouncycastle.operator.bc.BcRSAContentSignerBuilder
import org.bouncycastle.util.io.pem.PemObjectGenerator
import org.bouncycastle.util.io.pem.PemWriter
import java.io.File
import java.io.InputStream
import java.io.StringWriter
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.util.Date

class KeyStoreHelper {

    companion object {
        const val KEYSTORE_INSTANCE_TYPE = "pkcs12"
    }

    /**
     * Generate a basic key store
     * @param keyStoreFile target file to use
     * @param alias unique name to use
     * @param password
     * @param x500Name to use within certificate builder, has default value
     */
    fun generateKeyStore(
        keyStoreFile: File,
        alias: String,
        password: String,
        x500Name: MemberX500Name = MemberX500Name.parse("CN=Default Signing Key, O=R3, L=London, c=GB")
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
        val certSerialNumber = BigInteger.TEN
        val endDate = Date(now + 100L * 60 * 60 * 24 * 1000)
        val bouncyCastleX500Name = x500Name.toBouncyCastleX500Name()
        val certificateBuilder =
            JcaX509v3CertificateBuilder(bouncyCastleX500Name, certSerialNumber, startDate, endDate, bouncyCastleX500Name, keyPair.public)
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

    /**
     * Import a given certificate into an existing key store
     * @param keyStoreFile file to use
     * @param keyStorePassword
     * @param certificateInputStream certificate value to be added
     * @param certificateAlias unique alias for the certificate
     * @param certificateFactoryType defaults to X.509
     */
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

    /**
     * Export a certificate from the keystore
     * @param keyStoreFile file to use
     * @param password
     * @param certificateAlias unique name to use
     * @param exportedCertFile target file to be generated
     */
    fun exportCertificateFromKeyStore(
        keyStoreFile: File,
        keyStorePassword: String,
        certificateAlias: String,
        exportedCertFile: File
    ) {
        val keyStore = KeyStore.getInstance(KEYSTORE_INSTANCE_TYPE)
        keyStore.load(keyStoreFile.inputStream(), keyStorePassword.toCharArray())
        val cert = keyStore.getCertificate(certificateAlias)
        val writer = StringWriter()
        PemWriter(writer).use { pw ->
            val gen: PemObjectGenerator = JcaMiscPEMGenerator(cert)
            pw.writeObject(gen)
        }
        exportedCertFile.writeText(writer.toString())
    }

    /**
     * The SDK stores the default gradle cert so others don't have to.
     * @return the content of the certificate
     */
    fun getDefaultGradleCertificateStream(): InputStream {
        return this::class.java.getResourceAsStream("/network/certificates/gradle-plugin-default-key.pem")!!
    }

    private fun MemberX500Name.toBouncyCastleX500Name(): X500Name {
        return X500Name(this.toString())
    }
}
