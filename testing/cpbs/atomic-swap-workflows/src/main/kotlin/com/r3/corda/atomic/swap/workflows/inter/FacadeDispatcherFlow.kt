package com.r3.corda.atomic.swap.workflows.inter

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.interop.FacadeService
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import org.slf4j.LoggerFactory

open class FacadeDispatcherFlow : ResponderFlow {

    @CordaInject
    lateinit var facadeService: FacadeService

    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }
    @Suspendable
    override fun call(session: FlowSession) {
        log.info("${this::class.java.simpleName}.call() starting")
        val request = session.receive(String::class.java)
        log.info("Processing $request")
        val facadeResponse = facadeService.dispatchFacadeRequest(this, request)
        log.info("Responding $facadeResponse")
        session.send(facadeResponse)
        log.info("${this::class.java.simpleName}.call() end")
    }
}