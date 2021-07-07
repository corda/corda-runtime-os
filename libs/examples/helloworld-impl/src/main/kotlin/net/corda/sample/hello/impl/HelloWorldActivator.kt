package net.corda.sample.hello.impl

import net.corda.v5.base.util.contextLogger
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.slf4j.Logger

@Component(immediate = true)
class HelloWorldActivator {
    private companion object {
        private val logger: Logger = contextLogger()
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

