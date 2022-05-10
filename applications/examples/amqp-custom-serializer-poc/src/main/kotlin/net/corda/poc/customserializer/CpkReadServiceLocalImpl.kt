package net.corda.poc.customserializer

import net.corda.cpk.read.CpkReadService
import net.corda.libs.packaging.CpkIdentifier
import net.corda.packaging.Cpi
import net.corda.packaging.Cpk
import org.osgi.service.component.annotations.Component
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Paths

interface CpkReadServiceLoader {
    fun load(cpiInputStream : InputStream) : Cpi
}

/** Do not copy me, do not use me anywhere else */
@Suppress("UNUSED")
@Component(service = [CpkReadService::class, CpkReadServiceLoader::class])
class CpkReadServiceLocalImpl : CpkReadService, CpkReadServiceLoader {
    private val cpkById = mutableMapOf<CpkIdentifier, Cpk>()

    private val tmpDir = Paths.get(System.getProperty("java.io.tmpdir"), "cpb")
        .apply { Files.createDirectories(this) }

    override val isRunning: Boolean = true
    override fun start() { }
    override fun stop() { }

    override fun load(cpiInputStream: InputStream) : Cpi {
        val cpi = Cpi.from(cpiInputStream, expansionLocation = tmpDir, verifySignature = true)

        cpi.cpks.forEach { cpk -> cpkById[CpkIdentifier.fromLegacy(cpk.metadata.id)] = cpk }

        return cpi
    }

    override fun get(cpkId: CpkIdentifier): Cpk? = cpkById[cpkId]
}
