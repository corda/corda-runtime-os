package net.corda.sample.hello.impl

import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Component(immediate = true)
class HelloWorldActivator {
    private companion object {
        private val logger: Logger = LoggerFactory.getLogger(HelloWorldActivator::class.java)
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