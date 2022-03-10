package net.corda.p2p.test.stub.certificates

import net.corda.p2p.test.KeyAlgorithm
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import java.io.File
import java.io.StringWriter
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.Certificate

abstract class StubCertificatesAuthority : AutoCloseable {
    abstract val caCertificate: Certificate
    companion object {
        const val PASSWORD = "password"

        fun createLocalAuthority(algorithm: KeyAlgorithm, home: File? = null): LocalCertificatesAuthority {
            return LocalCertificatesAuthorityImpl(algorithm, home)
        }
        fun createRemoteAuthority(
            apiKey: String,
            passPhrase: String,
            email: String,
            authorityName: String,
        ): StubCertificatesAuthority {
            return TinyCertCertificatesAuthority(
                apiKey,
                passPhrase,
                email,
                authorityName,
            )
        }

        fun Certificate.toPem(): String {
            return StringWriter().use { str ->
                JcaPEMWriter(str).use { writer ->
                    writer.writeObject(this)
                }
                str.toString()
            }
        }
        fun Certificate.toKeystore(): KeyStore {
            return KeyStore.getInstance("PKCS12").also { keyStore ->
                keyStore.load(null)
                keyStore.setCertificateEntry("alias", this)
            }
        }
    }

    fun prepareKeyStore(host: String) = prepareKeyStore(listOf(host))
    abstract fun prepareKeyStore(hosts: Collection<String>): PrivateKeyWithCertificate

    data class PrivateKeyWithCertificate(
        val privateKey: PrivateKey,
        val certificate: Certificate,
    ) {
        fun toKeyStore(): KeyStore {
            val keyStore = KeyStore.getInstance("PKCS12").also { keyStore ->
                keyStore.load(null)
                keyStore.setKeyEntry("entry", privateKey, PASSWORD.toCharArray(), arrayOf(certificate))
            }
            return keyStore
        }

        fun certificatePem(): String {
            return certificate.toPem()
        }
    }
}
