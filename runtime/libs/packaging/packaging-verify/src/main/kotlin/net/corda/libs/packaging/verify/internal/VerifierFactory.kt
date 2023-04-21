package net.corda.libs.packaging.verify.internal

import net.corda.libs.packaging.PackagingConstants.CPB_FORMAT_ATTRIBUTE
import net.corda.libs.packaging.PackagingConstants.CPI_FORMAT_ATTRIBUTE
import net.corda.libs.packaging.PackagingConstants.CPK_FORMAT_ATTRIBUTE
import net.corda.libs.packaging.core.exception.CordappManifestException
import net.corda.libs.packaging.verify.JarReader
import net.corda.libs.packaging.verify.PackageType
import net.corda.libs.packaging.verify.Verifier
import net.corda.libs.packaging.verify.internal.cpb.CpbV2Verifier
import net.corda.libs.packaging.verify.internal.cpb.CpbVerifier
import net.corda.libs.packaging.verify.internal.cpi.CpiV2Verifier
import net.corda.libs.packaging.verify.internal.cpi.CpiVerifier
import net.corda.libs.packaging.verify.internal.cpk.CpkV2Verifier
import net.corda.libs.packaging.verify.internal.cpk.CpkVerifier
import java.io.InputStream
import java.security.cert.X509Certificate as X509Certificate1

/**
 * Creates CPx verifier
 */
object VerifierFactory {
    const val FORMAT_2 = "2.0"

    fun createCpVerifier(type: PackageType, format: String?, jarReader: JarReader): Verifier =
        when (type) {
            PackageType.CPK -> createCpkVerifier(format, jarReader)
            PackageType.CPB -> createCpbVerifier(format, jarReader)
            PackageType.CPI -> createCpiVerifier(format, jarReader)
        }

    /** Creates CPK verifier for format specified in the Manifest of the package */
    fun createCpkVerifier(name: String, inputStream: InputStream, trustedCerts: Collection<X509Certificate1>): CpkVerifier {
        val jarReader = JarReader(name, inputStream, trustedCerts)
        val format = jarReader.manifest.mainAttributes.getValue(CPK_FORMAT_ATTRIBUTE)
        return createCpkVerifier(format, jarReader)
    }

    /** Creates CPK verifier for specified format */
    fun createCpkVerifier(format: String?, jarReader: JarReader): CpkVerifier {
        return when (format) {
            FORMAT_2 -> CpkV2Verifier(jarReader)
            else -> throw CordappManifestException("Unsupported CPK format \"$format\"")
        }
    }

    /** Creates CPB verifier for format specified in the Manifest of the package */
    fun createCpbVerifier(
        name: String, inputStream: InputStream, trustedCerts: Collection<X509Certificate1>): CpbVerifier {
        val jarReader = JarReader(name, inputStream, trustedCerts)
        val format = jarReader.manifest.mainAttributes.getValue(CPB_FORMAT_ATTRIBUTE)
        return createCpbVerifier(format, jarReader)
    }

    /** Creates CPB verifier for specified format */
    fun createCpbVerifier(
        format: String?, name: String, inputStream: InputStream, trustedCerts: Collection<X509Certificate1>): CpbVerifier {
        val jarReader = JarReader(name, inputStream, trustedCerts)
        return createCpbVerifier(format, jarReader)
    }

    /** Creates CPB verifier for specified format */
    fun createCpbVerifier(format: String?, jarReader: JarReader): CpbVerifier {
        return when (format) {
            FORMAT_2 -> CpbV2Verifier(jarReader)
            else -> throw CordappManifestException("Unsupported CPB format \"$format\"")
        }
    }

    /** Creates CPI verifier for format specified in the Manifest of the package */
    fun createCpiVerifier(name: String, inputStream: InputStream, trustedCerts: Collection<X509Certificate1>): CpiVerifier {
        val jarReader = JarReader(name, inputStream, trustedCerts)
        val format = jarReader.manifest.mainAttributes.getValue(CPI_FORMAT_ATTRIBUTE)
        return createCpiVerifier(format, jarReader)
    }

    /** Creates CPI verifier for specified format */
    fun createCpiVerifier(
        format: String?, name: String, inputStream: InputStream, trustedCerts: Collection<X509Certificate1>): CpiVerifier {
        val jarReader = JarReader(name, inputStream, trustedCerts)
        return createCpiVerifier(format, jarReader)
    }

    /** Creates CPI verifier for specified format */
    fun createCpiVerifier(format: String?, jarReader: JarReader): CpiVerifier {
        return when (format) {
            FORMAT_2 -> CpiV2Verifier(jarReader)
            else -> throw CordappManifestException("Unsupported CPI format \"$format\"")
        }
    }
}