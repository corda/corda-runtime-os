package net.corda.libs.configuration.write

import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Deactivate
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class CordaWriteServiceActivator {
    private companion object {
        private val logger: Logger = LoggerFactory.getLogger(CordaWriteServiceActivator::class.java)
    }

    @Activate
    fun start() {
        logger.info("Starting!")
    }

    @Deactivate
    fun stop() {
        logger.info("Stopping!")
    }
}