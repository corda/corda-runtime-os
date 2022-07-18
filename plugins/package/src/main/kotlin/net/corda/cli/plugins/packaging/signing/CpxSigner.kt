package net.corda.cli.plugins.packaging.signing

import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.CertPath
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.util.zip.ZipFile
import jdk.security.jarsigner.JarSigner

internal object CpxSigner {
    /**
     * Signs Cpx jar files.
     */
    fun sign(
        unsignedInputCpx: Path,
        signedOutputCpx: Path,
        privateKey: PrivateKey,
        certPath: CertPath,
        signerName: String,
        tsaUrl: String?
    ) {
        ZipFile(unsignedInputCpx.toFile()).use { unsignedCpi ->
            Files.newOutputStream(signedOutputCpx,
                StandardOpenOption.WRITE,
                StandardOpenOption.CREATE_NEW
            ).use { signedCpi ->

                // Create JarSigner
                val builder = JarSigner.Builder(privateKey, certPath)
                    .signerName(signerName)

                // Use timestamp server if provided
                tsaUrl?.let { builder.tsa(URI(it)) }

                // Sign CPI
                builder
                    .build()
                    .sign(unsignedCpi, signedCpi)
            }
        }
    }

    /**
     * Reads PrivateKeyEntry from key store
     */
    fun getPrivateKeyEntry(keyStoreFileName: String, keyStorePass: String, keyAlias: String): KeyStore.PrivateKeyEntry {
        val passwordCharArray = keyStorePass.toCharArray()
        val keyStore = KeyStore.getInstance(File(keyStoreFileName), passwordCharArray)

        when (val keyEntry = keyStore.getEntry(keyAlias, KeyStore.PasswordProtection(passwordCharArray))) {
            is KeyStore.PrivateKeyEntry -> return keyEntry
            else -> error("Alias \"${keyAlias}\" is not a private key")
        }
    }

    /**
     * Type of CertificateFactory to use. "X.509" is a required type for Java implementations.
     */
    private const val STANDARD_CERT_FACTORY_TYPE = "X.509"

    /**
     * Builds CertPath from certificate chain
     */
    fun buildCertPath(certificateChain: List<Certificate>) =
        CertificateFactory
            .getInstance(STANDARD_CERT_FACTORY_TYPE)
            .generateCertPath(certificateChain)
}