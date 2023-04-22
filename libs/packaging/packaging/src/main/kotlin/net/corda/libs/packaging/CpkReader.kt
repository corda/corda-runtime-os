package net.corda.libs.packaging

import net.corda.libs.packaging.core.CpkFormatVersion
import net.corda.libs.packaging.core.exception.CordappManifestException
import net.corda.libs.packaging.core.exception.UnknownFormatVersionException
import net.corda.libs.packaging.internal.FormatVersionReader
import net.corda.libs.packaging.internal.v2.CpkLoaderV2
import org.osgi.framework.Constants.BUNDLE_SYMBOLICNAME
import java.io.InputStream
import java.nio.file.Path
import java.util.Collections.singletonList
import java.util.jar.JarInputStream

object CpkReader {
    private val SYNTHETIC_CONTRACTS = singletonList("org.jetbrains.kotlin.osgi-bundle")
    private val version2 = CpkFormatVersion(2, 0)

    fun readCpk(
        inputStream: InputStream,
        cacheDir: Path,
        cpkLocation: String? = null,
        verifySignature: Boolean = jarSignatureVerificationEnabledByDefault(),
        cpkFileName: String? = null
    ): Cpk {

        // Read input stream, so we can process it through different classes that will consume the stream
        val buffer = inputStream.readAllBytes()

        // Read format version
        val manifest = JarInputStream(buffer.inputStream()).use(JarInputStream::getManifest)
            ?: throw CordappManifestException("No manifest in Jar file")

        return if (manifest.mainAttributes.getValue(BUNDLE_SYMBOLICNAME) in SYNTHETIC_CONTRACTS) {
            CpkLoaderV2().loadAsSyntheticContract(buffer, cacheDir, cpkFileName)
        } else {
            // Choose correct implementation to read this version
            when (val formatVersion = FormatVersionReader.readCpkFormatVersion(manifest)) {
                version2 -> CpkLoaderV2().loadCPK(buffer, cacheDir, cpkLocation, verifySignature, cpkFileName)
                else -> throw UnknownFormatVersionException("Unknown Corda-CPK-Format - \"$formatVersion\"")
            }
        }
    }
}
