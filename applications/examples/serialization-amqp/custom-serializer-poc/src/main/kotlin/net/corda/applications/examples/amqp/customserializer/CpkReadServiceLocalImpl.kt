package net.corda.applications.examples.amqp.customserializer

import net.corda.cpk.read.CpkReadService
import net.corda.libs.packaging.CpkIdentifier
import net.corda.packaging.CPI
import net.corda.packaging.CPK
import org.osgi.service.component.annotations.Component
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Paths

interface CpkReadServiceLoader {
    fun load(cpiInputStream : InputStream) : CPI
}

/** Do not copy me, do not use me anywhere else */
@Suppress("UNUSED")
@Component(service = [CpkReadService::class, CpkReadServiceLoader::class])
class CpkReadServiceLocalImpl : CpkReadService, CpkReadServiceLoader {
    private val cpkById = mutableMapOf<CpkIdentifier, CPK>()

    private val tmpDir = Paths.get(System.getProperty("java.io.tmpdir"), "cpb")
        .apply { Files.createDirectories(this) }

    override val isRunning: Boolean = true
    override fun start() { }
    override fun stop() { }

    override fun load(cpiInputStream: InputStream) : CPI {
        val cpi = CPI.from(cpiInputStream, expansionLocation = tmpDir, verifySignature = true)

        cpi.cpks.forEach { cpk -> cpkById[CpkIdentifier.fromLegacy(cpk.metadata.id)] = cpk }

        return cpi
    }

    override fun get(cpkId: CpkIdentifier): CPK? = cpkById[cpkId]
}
