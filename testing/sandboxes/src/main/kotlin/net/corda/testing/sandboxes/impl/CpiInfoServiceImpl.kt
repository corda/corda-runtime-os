package net.corda.testing.sandboxes.impl

import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.packaging.core.CpiMetadata
import net.corda.lifecycle.LifecycleCoordinatorName
import net.corda.reconciliation.VersionedRecord
import net.corda.testing.sandboxes.CpiLoader
import net.corda.testing.sandboxes.SandboxSetup
import net.corda.v5.base.util.loggerFor
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.propertytypes.ServiceRanking
import java.util.stream.Stream

@Suppress("unused")
@Component
@ServiceRanking(SandboxSetup.SANDBOX_SERVICE_RANKING)
class CpiInfoServiceImpl @Activate constructor(
    @Reference
    private val loader: CpiLoader
): CpiInfoReadService {
    private val logger = loggerFor<CpiInfoServiceImpl>()

    override val lifecycleCoordinatorName = LifecycleCoordinatorName.forComponent<CpiInfoReadService>()

    override fun getAllVersionedRecords(): Stream<VersionedRecord<CpiIdentifier, CpiMetadata>> =
        getAll().stream().map {
            object : VersionedRecord<CpiIdentifier, CpiMetadata> {
                override val version = it.version
                override val isDeleted = false
                override val key = it.cpiId
                override val value = it
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
