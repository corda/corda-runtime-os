package net.corda.libs.packaging

import net.corda.libs.packaging.core.CpkFormatVersion
import net.corda.libs.packaging.core.exception.CordappManifestException
import net.corda.libs.packaging.core.exception.UnknownFormatVersionException
import net.corda.libs.packaging.internal.FormatVersionReader
import net.corda.libs.packaging.internal.v2.CpiLoaderV2
import java.io.InputStream
import java.nio.file.Path
import java.util.jar.JarInputStream

class CpiReader(activeCordaPlatformVersion: Int) {
    private val version2 = CpkFormatVersion(2, 0)
    private val cpiLoader = CpiLoaderV2(activeCordaPlatformVersion)

    /**
     * Parses a CPI file and stores its information in a [Cpi] instance
     * @param inputStream a stream with the CPI file content
     * @param expansionLocation a filesystem directory where the CPK files
     * contained in the provided CPI will be extracted
     * @param cpiLocation an optional string containing information about the cpi location that will be used
     * in exception messages to ease troubleshooting
     * @param verifySignature whether to verify CPI and CPK files signatures
     * @return a new [Cpi] instance containing information about the provided CPI
     */
    fun readCpi(
        inputStream: InputStream,
        expansionLocation: Path,
        cpiLocation: String? = null,
        verifySignature: Boolean = jarSignatureVerificationEnabledByDefault()
    ): Cpi {
        // Read input stream, so we can process it through different classes that will consume the stream
        val buffer = inputStream.readAllBytes()

        // Read format version
        val manifest = JarInputStream(buffer.inputStream()).use(JarInputStream::getManifest)
            ?: throw CordappManifestException("No manifest in Jar file")

        // Choose correct implementation to read this version
        return when (val formatVersion = FormatVersionReader.readCpiFormatVersion(manifest)) {
            version2 -> cpiLoader.loadCpi(buffer, expansionLocation, cpiLocation, verifySignature)
            else -> throw UnknownFormatVersionException("Unknown Corda-CPI-Format - \"$formatVersion\"")
        }
    }
}
