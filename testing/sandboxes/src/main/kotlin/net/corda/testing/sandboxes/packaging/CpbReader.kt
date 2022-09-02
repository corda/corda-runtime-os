package net.corda.testing.sandboxes.packaging

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.file.Path
import java.util.jar.JarInputStream
import net.corda.libs.packaging.Cpi
import net.corda.libs.packaging.core.CpkFormatVersion
import net.corda.libs.packaging.core.exception.CordappManifestException
import net.corda.libs.packaging.internal.FormatVersionReader
import net.corda.testing.sandboxes.packaging.internal.CpbLoaderV2

/**
 * Parses a CPB into a [Cpi], leaving CPI related fields nulled out. This reader might be useful in testing cases where
 * we have a .cpb file and we are only interested in testing at CPB level and not interested in CPI fields.
 */
object CpbReader {
    private val version2 = CpkFormatVersion(2, 0)

    fun readCpi(
        cpbInputStream: InputStream,
        expansionLocation: Path,
        cpiLocation: String? = null,
        verifySignature: Boolean
    ): Cpi {
        // Read input stream, so we can process it through different classes that will consume the stream
        val cpbBytes = cpbInputStream.readAllBytes()

        val manifest = JarInputStream(ByteArrayInputStream(cpbBytes)).use {
            it.manifest
        } ?: throw CordappManifestException("No manifest in Jar file")

        val formatVersion = FormatVersionReader.readCpbFormatVersion(manifest)
        require(formatVersion == version2)

        return CpbLoaderV2()
            .loadCpi(
                cpbBytes,
                expansionLocation,
                cpiLocation,
                verifySignature
            )
    }
}

