package net.corda.components.examples.runflow

import net.corda.v5.application.flows.Flow
import net.corda.v5.application.injection.CordaInject
import net.corda.v5.application.node.CordaClock
import net.corda.v5.base.util.contextLogger

class DemoFlow: Flow<Unit> {

    private companion object {
        val log = contextLogger()
    }

    @CordaInject
    private lateinit var clock: CordaClock

    override fun call() {
        log.info("Starting Demo Flow")
        log.info("Sleeping for 10ms at ${clock.instant()}")
        Thread.sleep(10)
        log.info("Woke up after 10ms at ${clock.instant()}")
    }
}
