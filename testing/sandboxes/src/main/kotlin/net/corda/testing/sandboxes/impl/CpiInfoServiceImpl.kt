package net.corda.testing.sandboxes.impl

import net.corda.cpiinfo.read.CpiInfoListener
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.libs.packaging.CpiIdentifier
import net.corda.libs.packaging.CpiMetadata
import net.corda.testing.sandboxes.CpiLoader
import net.corda.v5.base.util.loggerFor
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Suppress("unused")
@Component
class CpiInfoServiceImpl @Activate constructor(
    @Reference
    private val loader: CpiLoader
): CpiInfoReadService {
    private val logger = loggerFor<CpiInfoServiceImpl>()

    override val isRunning: Boolean
        get() = true

    override fun getAll(): List<CpiMetadata> {
        val cpiList = loader.getAll()
        return cpiList.get()
    }

    override fun get(identifier: CpiIdentifier): CpiMetadata? {
        val cpiFile = loader.get(identifier)
        return cpiFile.get()
    }

    override fun registerCallback(listener: CpiInfoListener): AutoCloseable {
        return AutoCloseable {}
    }

    override fun start() {
        logger.info("Started")
    }

    override fun stop() {
        logger.info("Stopped")
    }
}
