package net.corda.libs.packaging.testutils.cpb.packaging.v2

import java.io.InputStream
import java.nio.file.Path
import java.util.jar.JarInputStream
import net.corda.libs.packaging.Cpi
import net.corda.libs.packaging.core.CpkFormatVersion
import net.corda.libs.packaging.core.exception.CordappManifestException
import net.corda.libs.packaging.readCpbFormatVersion
import net.corda.utilities.time.UTCClock

/**
 * Parses a .cpb file V2 into a [Cpi], leaving CPI related fields nulled out. This reader might be useful
 * in testing cases where we have a .cpb file and we are only interested in testing at CPB level and
 * not interested in CPI fields.
 */
object TestCpbReaderV2 {
    private val version2 = CpkFormatVersion(2, 0)

    fun readCpi(
        cpbInputStream: InputStream,
        expansionLocation: Path,
        cpiLocation: String? = null
    ): Cpi {
        // Read input stream, so we can process it through different classes that will consume the stream
        val cpbBytes = cpbInputStream.readAllBytes()

        val manifest = JarInputStream(cpbBytes.inputStream()).use(JarInputStream::getManifest)
            ?: throw CordappManifestException("No manifest in Jar file")

        val formatVersion = readCpbFormatVersion(manifest)
        require(formatVersion == version2)

        return TestCpbLoaderV2(UTCClock())
            .loadCpi(
                cpbBytes,
                expansionLocation,
                cpiLocation
            )
    }
}
