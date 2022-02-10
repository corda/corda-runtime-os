package net.corda.flow.sandbox

import net.corda.cpiinfo.read.CpiInfoListener
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.packaging.CPI
import net.corda.v5.base.util.loggerFor
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Suppress("unused")
@Component
class CpiInfoServiceImpl @Activate constructor(
    @Reference
    private val loader: LoaderService
): CpiInfoReadService {
    private val logger = loggerFor<CpiInfoServiceImpl>()

    override val isRunning: Boolean
        get() = true

    override fun get(identifier: CPI.Identifier): CPI.Metadata? {
        return loader.get(identifier).get()?.metadata
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
