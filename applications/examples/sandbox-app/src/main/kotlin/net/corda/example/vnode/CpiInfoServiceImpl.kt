package net.corda.example.vnode

import net.corda.cpiinfo.read.CpiInfoListener
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.cpiinfo.write.CpiInfoWriteService
import net.corda.packaging.CPI
import net.corda.v5.base.util.loggerFor
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

interface CpiInfoService : CpiInfoReadService, CpiInfoWriteService

@Suppress("unused")
@Component(service = [
    CpiInfoService::class,
    CpiInfoReadService::class,
    CpiInfoWriteService::class
])
class CpiInfoServiceImpl @Activate constructor(
    @Reference
    private val loader: LoaderService
): CpiInfoService {
    private val logger = loggerFor<CpiInfoService>()

    override val isRunning: Boolean
        get() = true

    override fun get(identifier: CPI.Identifier): CPI.Metadata? {
        return loader.get(identifier).get()?.metadata
    }

    override fun put(cpiMetadata: CPI.Metadata) {
        throw UnsupportedOperationException("put - not implemented")
    }

    override fun remove(cpiMetadata: CPI.Metadata) {
        loader.remove(cpiMetadata.id)
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
