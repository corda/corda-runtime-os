package net.corda.osgi.framework.apptester

import net.corda.osgi.framework.api.Lifecycle

class AppTester : Lifecycle {

    init {
        println("net.corda.osgi.framework.apptester.AppTester.INIT")
    }

    override fun startup(args: Array<String>) {
        println("net.corda.osgi.framework.apptester.AppTester.START($args)")
    }

    override fun shutdown() {
        println("net.corda.osgi.framework.apptester.AppTester.STOP")
    }

}