package net.corda.sample.hello.impl

import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Deactivate
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Component(immediate = true)
class HelloWorldActivator : BundleActivator {
    private companion object {
        private val logger: Logger = LoggerFactory.getLogger(HelloWorldActivator::class.java)
    }

    @Activate
    override fun start(context: BundleContext) {
        logger.info("Starting!")
        println("Starting! WooHoo!")
    }

    @Deactivate
    override fun stop(context: BundleContext) {
        logger.info("Stopping!")
        println("Stopping! Boo!!!")
    }
}
