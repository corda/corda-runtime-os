package net.corda.testing.driver.sandbox

import java.util.stream.Stream
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.reconciliation.VersionedRecord
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.propertytypes.ServiceRanking
import org.slf4j.LoggerFactory

@Suppress("unused")
@Component(service = [ CpiInfoReadService::class ], property = [ DRIVER_SERVICE ])
@ServiceRanking(DRIVER_SERVICE_RANKING)
class CpiInfoServiceImpl @Activate constructor(
    @Reference
    private val loader: CpiLoader
) : CpiInfoReadService {
    private val logger = LoggerFactory.getLogger(this::class.java)

    override val lifecycleCoordinatorName = LifecycleCoordinatorName.forComponent<CpiInfoReadService>()

    override fun getAllVersionedRecords(): Stream<VersionedRecord<CpiIdentifier, CpiMetadata>> =
        getAll().stream().map { cpi ->
            object : VersionedRecord<CpiIdentifier, CpiMetadata> {
                override val version get() = cpi.version
                override val isDeleted get() = false
                override val key get() = cpi.cpiId
                override val value get() = cpi
            }
        }

    override val isRunning: Boolean
        get() = true

    override fun getAll(): List<CpiMetadata> {
        val cpiList = loader.getAllCpiMetadata()
        return cpiList.get()
    }

    override fun get(identifier: CpiIdentifier): CpiMetadata? {
        val cpiFile = loader.getCpiMetadata(identifier)
        return cpiFile.get()
    }

    override fun start() {
        logger.info("Started")
    }

    override fun stop() {
        logger.info("Stopped")
    }
}
