package net.corda.osgi.framework.test

import net.corda.osgi.api.Lifecycle
import net.corda.osgi.api.ShutdownService
import org.osgi.framework.Bundle

internal class AppTester : Lifecycle {

    private lateinit var bundle: Bundle

    init {
        println("net.corda.osgi.framework.apptester.AppTester.INIT")
    }



    override fun startup(args: Array<String>, bundle: Bundle) {
        this.bundle = bundle
        println("net.corda.osgi.framework.apptester.AppTester.START($args)")
        Thread.sleep(1000)
        Thread {
            val shutdownReference = bundle.bundleContext.getServiceReference(ShutdownService::class.java.name)
            if (shutdownReference != null) {
                val shutdownService: ShutdownService? =
                    bundle.bundleContext.getService(shutdownReference) as ShutdownService
                if (shutdownService != null) {
                    shutdownService.shutdown(bundle)
                } else {
                    throw ClassNotFoundException("Service reference to ${ShutdownService::class.java.name} not found.")
                }
            } else {
                throw ClassNotFoundException("Service ${ShutdownService::class.java.name} not found.")
            }
        }.start()
    }

    override fun shutdown(bundle: Bundle) {
        println("net.corda.osgi.framework.apptester.AppTester.STOP")
    }

}