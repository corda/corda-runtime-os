package net.corda.flow.testing.fakes

import net.corda.cpiinfo.read.CpiInfoListener
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.libs.packaging.CpiIdentifier
import net.corda.libs.packaging.CpiMetadata
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.propertytypes.ServiceRanking

@ServiceRanking(Int.MAX_VALUE)
@Component(service = [CpiInfoReadService::class, FakeCpiInfoReadService::class])
class FakeCpiInfoReadService : CpiInfoReadService {

    private val cpiData = mutableMapOf<CpiIdentifier, CpiMetadata>()

    fun add(cpiMetadata: CpiMetadata) {
        val cpi = get(cpiMetadata.cpiId)
        if (cpi == null) {
            cpiData[cpiMetadata.cpiId] = cpiMetadata
        } else {
            val combined = cpi.copy(cpksMetadata = cpi.cpksMetadata + cpiMetadata.cpksMetadata)
            cpiData[combined.cpiId] = combined
        }
    }

    fun reset(){
        cpiData.clear()
    }

    override fun getAll(): List<CpiMetadata> {
        return cpiData.values.toList()
    }

    override fun get(identifier: CpiIdentifier): CpiMetadata? {
        return cpiData[identifier]
    }

    override fun registerCallback(listener: CpiInfoListener): AutoCloseable {
        TODO("Not yet implemented")
    }

    override val isRunning: Boolean
        get() = true

    override fun start() {
    }

    override fun stop() {
    }
}