package net.corda.osgi.framework.apptester

import org.osgi.framework.BundleActivator
import org.osgi.framework.BundleContext
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Deactivate

class AppTester : BundleActivator {

    companion object {

        @JvmStatic
        fun main(args: Array<String>) {
            println("net.corda.osgi.framework.apptester.AppTester MAIN")
        }

    } //~ companion object

    @Activate
    override fun start(context: BundleContext?) {
        println("net.corda.osgi.framework.apptester.AppTester START")
    }

    @Deactivate
    override fun stop(context: BundleContext?) {
        println("net.corda.osgi.framework.apptester.AppTester STOP")
    }
}