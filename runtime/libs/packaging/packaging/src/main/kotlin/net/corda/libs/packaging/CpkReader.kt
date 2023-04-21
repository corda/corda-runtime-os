package net.corda.libs.packaging

import net.corda.libs.packaging.core.CpkFormatVersion
import net.corda.libs.packaging.core.exception.CordappManifestException
import net.corda.libs.packaging.core.exception.UnknownFormatVersionException
import net.corda.libs.packaging.internal.FormatVersionReader
import net.corda.libs.packaging.internal.v2.CpkLoaderV2
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.file.Path
import java.util.jar.JarInputStream

class CpkReader {
    companion object {
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
            val manifest = ByteArrayInputStream(buffer).use {
                JarInputStream(it).use { it.manifest }
            } ?: throw CordappManifestException("No manifest in Jar file")
            val formatVersion = FormatVersionReader.readCpkFormatVersion(manifest)

            // Choose correct implementation to read this version
            return when (formatVersion) {
                version2 -> CpkLoaderV2().loadCPK(buffer, cacheDir, cpkLocation, verifySignature, cpkFileName)
                else -> throw UnknownFormatVersionException("Unknown Corda-CPK-Format - \"$formatVersion\"")
            }
        }
    }
}
