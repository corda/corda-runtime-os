package net.corda.sample.impl.hello

import org.osgi.annotation.bundle.Header
import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext
import org.osgi.framework.Constants
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Header(name = Constants.BUNDLE_ACTIVATOR, value = "\${@class}")
class HelloWorldActivator : BundleActivator {
    private companion object {
        private val logger: Logger = LoggerFactory.getLogger(HelloWorldActivator::class.java)
    }

    override fun start(context: BundleContext) {
        logger.info("Starting!")
        println("Starting! WooHoo!")
    }

    override fun stop(context: BundleContext) {
        logger.info("Stopping!")
        println("Stopping! Boo!!!")
    }
}