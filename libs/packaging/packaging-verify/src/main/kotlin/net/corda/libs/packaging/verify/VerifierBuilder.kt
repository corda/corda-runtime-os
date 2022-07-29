package net.corda.libs.packaging.verify

import net.corda.libs.packaging.PackagingConstants.CPB_FORMAT_ATTRIBUTE
import net.corda.libs.packaging.PackagingConstants.CPI_FORMAT_ATTRIBUTE
import net.corda.libs.packaging.PackagingConstants.CPK_FORMAT_ATTRIBUTE
import net.corda.libs.packaging.verify.internal.VerifierFactory
import java.io.InputStream
import java.util.jar.Manifest
import java.security.cert.X509Certificate as X509Certificate1

/**
 * Creates CPK/CPB/CPI verifier
 */
class VerifierBuilder {
    companion object {
        private const val EXTENSION_LEN = 4
        private val extensionToType = mapOf(
            ".cpk" to PackageType.CPK,
            ".cpb" to PackageType.CPB,
            ".cpi" to PackageType.CPI
        )
        private val typeToFormatAttribute = mapOf(
            PackageType.CPK to CPK_FORMAT_ATTRIBUTE,
            PackageType.CPB to CPB_FORMAT_ATTRIBUTE,
            PackageType.CPI to CPI_FORMAT_ATTRIBUTE
        )
    }

    var type: PackageType? = null
        private set
    var format: String? = null
        private set
    var name: String? = null
        private set
    var inputStream: InputStream? = null
        private set
    var trustedCerts: Collection<X509Certificate1>? = null
        private set

    /** Package type (if not provided, it is determined from the file name extension) */
    fun type(type: PackageType?) = apply { this.type = type }
    /** Package format (if not provided, it is determined from the Manifest) */
    fun format(format: String?) = apply { this.format = format }
    /** Package file name */
    fun name(name: String?) = apply { this.name = name }
    /** Package input stream */
    fun inputStream(inputStream: InputStream?) = apply { this.inputStream = inputStream }
    /** Trusted certificates */
    fun trustedCerts(trustedCerts: Collection<X509Certificate1>?) = apply { this.trustedCerts = trustedCerts }

    fun build(): Verifier {
        check(name != null) { "Name not set" }
        check(inputStream != null) { "InputStream not set" }
        check(trustedCerts != null) { "Trusted certificates not set" }
        if (type == null)
            type = typeFromName() ?: throw IllegalStateException("Type not set and cannot be determined from file name")
        val jarReader = JarReader(name!!, inputStream!!, trustedCerts!!)
        if (format == null)
            format = formatFromManifest(jarReader.manifest)
        return VerifierFactory.createCpVerifier(type!!, format, jarReader)
    }

    private fun typeFromName(): PackageType? =
        name?.let {
            if (it.length < EXTENSION_LEN) null
            else extensionToType[it.substring(it.length - EXTENSION_LEN, it.length).lowercase()]
        }

    private fun formatFromManifest(manifest: Manifest): String? =
        manifest.mainAttributes.getValue(typeToFormatAttribute[type])

}