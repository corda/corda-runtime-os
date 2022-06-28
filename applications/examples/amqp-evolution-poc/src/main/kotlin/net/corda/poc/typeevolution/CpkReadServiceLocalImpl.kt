package net.corda.poc.typeevolution

import net.corda.cpk.read.CpkReadService
import net.corda.libs.packaging.Cpi
import net.corda.libs.packaging.CpiReader
import net.corda.libs.packaging.Cpk
import org.osgi.service.component.annotations.Component
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Paths
import net.corda.v5.crypto.SecureHash

interface CpkReadServiceLoader {
    fun load(cpiInputStream : InputStream) : Cpi
}

/** Do not copy me, do not use me anywhere else */
@Suppress("UNUSED")
@Component(service = [CpkReadService::class, CpkReadServiceLoader::class])
class CpkReadServiceLocalImpl : CpkReadService, CpkReadServiceLoader {
    private val cpkByFileChecksum = mutableMapOf<SecureHash, Cpk>()

    private val tmpDir = Paths.get(System.getProperty("java.io.tmpdir"), "cpb")
        .apply { Files.createDirectories(this) }

    override val isRunning: Boolean = true
    override fun start() { }
    override fun stop() { }

    override fun load(cpiInputStream: InputStream): Cpi {
        val cpi = CpiReader.readCpi(cpiInputStream, expansionLocation = tmpDir, verifySignature = true)

        cpi.cpks.forEach { cpk -> cpkByFileChecksum[cpk.metadata.fileChecksum] = cpk }

        return cpi
    }

    override fun get(cpkFileChecksum: SecureHash): Cpk? = cpkByFileChecksum[cpkFileChecksum]
}
