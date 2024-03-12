package net.corda.sdk.packaging.signing

import jdk.security.jarsigner.JarSigner
import net.corda.libs.packaging.verify.SigningHelpers.isSigningRelated
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.KeyStore
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.util.jar.JarInputStream
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.zip.ZipFile

object SigningHelpers {
    /**
     * Signs Cpx jar files.
     */
    fun sign(
        unsignedInputCpx: Path,
        signedOutputCpx: Path,
        signingOptions: SigningOptions,
    ) {
        ZipFile(unsignedInputCpx.toFile()).use { unsignedCpi ->
            Files.newOutputStream(
                signedOutputCpx,
                StandardOpenOption.WRITE,
                StandardOpenOption.CREATE_NEW
            ).use { signedCpi ->

                val privateKeyEntry = getPrivateKeyEntry(
                    signingOptions.keyStoreFileName,
                    signingOptions.keyStorePass,
                    signingOptions.keyAlias
                )
                val privateKey = privateKeyEntry.privateKey
                val certPath = buildCertPath(privateKeyEntry.certificateChain.asList())

                // Create JarSigner
                val signerName = signingOptions.signatureFile ?: getSignerNameFromString(signingOptions.keyAlias)
                val builder = JarSigner.Builder(privateKey, certPath)
                    .signerName(signerName)

                // Use timestamp server if provided
                signingOptions.tsaUrl?.let { builder.tsa(URI(it)) }

                // Sign CPI
                builder
                    .build()
                    .sign(unsignedCpi, signedCpi)
            }
        }
    }

    @Suppress("NestedBlockDepth")
    fun removeSignatures(signedCpx: Path, removedSignaturesCpx: Path) {
        JarInputStream(Files.newInputStream(signedCpx, StandardOpenOption.READ)).use { inputJar ->
            val manifest = Manifest()
            // Leave out manifest signature entries, so only get main attributes.
            manifest.mainAttributes.putAll(inputJar.manifest.mainAttributes)
            JarOutputStream(Files.newOutputStream(removedSignaturesCpx, StandardOpenOption.WRITE), manifest).use { outputJar ->
                var nextEntry = inputJar.nextJarEntry
                while (nextEntry != null) {
                    if (!isSigningRelated(nextEntry)) {
                        outputJar.putNextEntry(nextEntry)
                        inputJar.copyTo(outputJar)
                        outputJar.closeEntry()
                    }
                    nextEntry = inputJar.nextJarEntry
                }
            }
        }
    }

    /**
     * Reads PrivateKeyEntry from key store
     */
    private fun getPrivateKeyEntry(keyStoreFileName: String, keyStorePass: String, keyAlias: String): KeyStore.PrivateKeyEntry {
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
    private fun buildCertPath(certificateChain: List<Certificate>) =
        CertificateFactory
            .getInstance(STANDARD_CERT_FACTORY_TYPE)
            .generateCertPath(certificateChain)

    // The following has the same behavior as jarsigner in terms of signature files naming.
    private fun getSignerNameFromString (keyAlias: String): String =
        keyAlias.run {
            var str = this
            if (str.length > 8) {
                str = str.substring(0, 8).uppercase()
            }
            val strBuilder = StringBuilder()
            for (c in str) {
                @Suppress("ComplexCondition")
                if (c in 'A'..'Z' || c in 'a'..'z' || c == '-' || c == '_') {
                    strBuilder.append(c)
                } else {
                    strBuilder.append('_')
                }
            }
            str = strBuilder.toString()
            str
        }
}
