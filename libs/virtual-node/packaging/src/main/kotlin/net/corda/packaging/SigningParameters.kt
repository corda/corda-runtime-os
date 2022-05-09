package net.corda.packaging

import jdk.security.jarsigner.JarSigner
import net.corda.v5.crypto.DigestAlgorithmName
import java.io.File
import java.io.OutputStream
import java.lang.IllegalArgumentException
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.util.zip.ZipFile

/**
 * Parameters for signing a jar file:
 * @property keyStore the keystore containing the private key
 * @property keyAlias alias name of the private key inside [keyStore]
 * @property keyPassword password (if any) for the private key inside the [keyStore]
 */
class SigningParameters(
        val keyStore: KeyStore,
        val keyAlias: String,
        val keyPassword: String? = null) {

    companion object {
        /**
         * Signs the jar file in [originalFile] using the signing parameters contained in [params] and dumps
         * the signed jar archive to [outputStream]
         */
        @JvmStatic
        fun sign(originalFile: File, outputStream: OutputStream, params: SigningParameters) {
            val signingCertificate = params.keyStore.getCertificate(params.keyAlias)
                    ?: throw IllegalArgumentException("The specified keystore does not contain a key with alias '${params.keyAlias}'")
            val certPath = CertificateFactory.getInstance("X.509")
                    .generateCertPath(listOf(signingCertificate))
            val key = params.keyStore.getKey(params.keyAlias, params.keyPassword?.toCharArray()) as PrivateKey
            JarSigner.Builder(key, certPath)
                    .digestAlgorithm(DigestAlgorithmName.SHA2_256.name)
                    .build()
                    .sign(ZipFile(originalFile), outputStream)
        }
    }
}