package net.corda.osgi.framework.apptester

import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext

class AppTester: BundleActivator {

    override fun start(context: BundleContext?) {
        println("START")
    }

    override fun stop(context: BundleContext?) {
        println("STOP")
    }
}