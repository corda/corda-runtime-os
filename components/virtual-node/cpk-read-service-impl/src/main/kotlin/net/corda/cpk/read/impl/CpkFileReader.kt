package net.corda.cpk.read.impl

import com.typesafe.config.Config
import net.corda.cpk.readwrite.CpkServiceConfigKeys
import net.corda.cpk.readwrite.resolvePath
import net.corda.packaging.CPK
import java.nio.file.Files
import java.nio.file.Path

/**
 * Temporary implementation that reads the CPKs from a common folder.
 *
 * This looks weird, because CPK.from, ultimately puts an identical cpk in its own internal cacheDir that's
 * assumed to be on a per "process" filesystem and not shared.
 */
class CpkFileReader(private val commonCpkCacheDir: Path) : AutoCloseable {
    companion object {
        fun fromConfig(cfg: Config): CpkFileReader {
            val commonCacheDir = cfg
                .getString(CpkServiceConfigKeys.CPK_CACHE_DIR)
                .let(Path::of)
            return CpkFileReader(commonCacheDir)
        }
    }

    private val cacheDir: Path

    init {
        commonCpkCacheDir.let((Files::createDirectories))
        cacheDir = Files.createTempDirectory(commonCpkCacheDir, "packageCache")
    }

    fun get(cpkMetadata: CPK.Metadata): CPK? {
        val cpkPath = cpkMetadata.resolvePath(commonCpkCacheDir)
        if (!Files.exists(cpkPath))
            return null

        val cpk = CPK.from(
            Files.newInputStream(cpkPath),
            cacheDir,
            cpkPath.toString(), // apparently is used in exceptions thrown via this `from` method.
            verifySignature = true
        )

        return cpk
    }

    override fun close() {
        // Commented out for the time being, but left to draw attention that we probably
        // want to clean up the cacheDir when we shut down a system (and not necessarily the worker).
        // There's no "policy" for this at the moment, or consistent location for writing files for a C5 system.
        // Under kafka we get a snapshot of chunks and can recreate the cache on restart, but
        // that adds to start up.
//        Files.walk(cacheDir)
//            .sorted(Comparator.reverseOrder())
//            .forEach(Files::delete)
    }
}
