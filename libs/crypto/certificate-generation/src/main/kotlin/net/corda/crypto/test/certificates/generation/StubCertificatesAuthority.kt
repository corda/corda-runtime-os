package net.corda.crypto.test.certificates.generation

import net.corda.v5.cipher.suite.schemes.SignatureSchemeTemplate
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import java.io.File
import java.io.StringWriter
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.Certificate
import java.time.Duration

/**
 * A "Certificate authority" to be used in test environment.
 *
 * Currently, this support two modes:
 * * TinyCert mode - Using https://www.tinycert.org/ to issue certificates. This support will support CRL but only RSA keys.
 * * Local mode - Using Bouncy castle to create the keys and certificate and save the data to the file
 * system (optional). This will not support CRL.
 *
 * For example:
 * ```
 * StubCertificatesAuthority.createLocalAuthority(ECDSA_SECP256K1_SHA256_TEMPLATE).use { ca ->
 *     val keyStore = ca.prepareKeyStore("www.r3.com").toKeyStore()
 *     val trustStore = caCertificate.toKeystore()
 * }
 * ```
 *
 */
abstract class StubCertificatesAuthority : AutoCloseable {
    companion object {
        /**
         * The password that is used when creating a key store.
         */
        const val PASSWORD = "password"

        /**
         * Create a new local authority.
         *
         * @param signatureSchemeTemplate - The signature template (currently, only RSA and EC are supported)
         * @param home - The home directory to save the CA private keys, certificate and serial numbers.
         *      If null the authority store will not be saved.
         *      If an authority was saved to the location, it will be reloaded.
         * @param validDuration - The duration after which the certificate will become invalid
         */
        fun createLocalAuthority(
            signatureSchemeTemplate: SignatureSchemeTemplate,
            home: File? = null,
            validDuration: Duration = Duration.ofDays(30),
        ): LocalCertificatesAuthority {
            return LocalCertificatesAuthorityImpl(signatureSchemeTemplate, home, validDuration)
        }

        /**
         * Create a TinyCert (https://www.tinycert.org/) based authority.
         *
         * @param apiKey - The TinyCert API key.
         * @param passPhrase - The TinyCert pass phrase.
         * @param email - The TinyCert login email address.
         * @param authorityName - The authority name (within TinyCert).
         */
        fun createTinyCertAuthority(
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

        /**
         * Convert a Certificate to PEM string.
         */
        fun Certificate.toPem(): String {
            return StringWriter().use { str ->
                JcaPEMWriter(str).use { writer ->
                    writer.writeObject(this)
                }
                str.toString()
            }
        }

        /**
         * Convert a certificate to a key store object (with alias "alias")
         */
        fun Certificate.toKeystore(): KeyStore {
            return KeyStore.getInstance("PKCS12").also { keyStore ->
                keyStore.load(null)
                keyStore.setCertificateEntry("alias", this)
            }
        }
    }

    /**
     * A data class that represent a private key and certificate pair.
     */
    data class PrivateKeyWithCertificate(
        val privateKey: PrivateKey,
        val certificate: Certificate,
    ) {

        /**
         * Convert the pair to a key store object with password: PASSWORD and alias: "entry".
         */
        fun toKeyStore(): KeyStore {
            val keyStore = KeyStore.getInstance("PKCS12").also { keyStore ->
                keyStore.load(null)
                keyStore.setKeyEntry("entry", privateKey, PASSWORD.toCharArray(), arrayOf(certificate))
            }
            return keyStore
        }

        /**
         * Convert the certificate to pem String.
         */
        fun certificatePem(): String {
            return certificate.toPem()
        }
    }

    /**
     * Return the CA certificate.
     */
    abstract val caCertificate: Certificate

    /**
     * Generate a key store with a single host name.
     *
     * @param host - the name of the host.
     * @return A private key and certificate.
     */
    fun generateKeyAndCertificate(host: String) = generateKeyAndCertificate(listOf(host))

    /**
     * Generate a key store with a list of hosts names.
     *
     * @param hosts - the list of hosts.
     * @return A private key and certificate.
     */
    abstract fun generateKeyAndCertificate(hosts: Collection<String>): PrivateKeyWithCertificate
}
