package net.corda.cpk.write.impl

import com.typesafe.config.Config
import net.corda.cpk.readwrite.CpkServiceConfigKeys
import net.corda.cpk.readwrite.resolvePath
import net.corda.packaging.CPK
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path

/**
 * Temporary implementation that writes the CPKs to a common folder
 */
class CpkFileWriter(private val commonCpkCacheDir: Path) {
    init {
        commonCpkCacheDir.let((Files::createDirectories))
    }

    companion object {
        fun fromConfig(cfg: Config): CpkFileWriter {
            val commonCacheDir = cfg
                .getString(CpkServiceConfigKeys.CPK_CACHE_DIR)
                .let(Path::of)
            return CpkFileWriter(commonCacheDir)
        }
    }

    fun put(cpkMetadata: CPK.Metadata, inputStream: InputStream) =
        Files.copy(inputStream, cpkMetadata.resolvePath(commonCpkCacheDir))

    fun remove(cpkMetadata: CPK.Metadata) {
        if (Files.exists(cpkMetadata.resolvePath(commonCpkCacheDir))) {
            Files.delete(cpkMetadata.resolvePath(commonCpkCacheDir))
        }
    }
}
