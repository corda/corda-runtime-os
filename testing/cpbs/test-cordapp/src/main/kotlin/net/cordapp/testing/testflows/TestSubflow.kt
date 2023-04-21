package net.cordapp.testing.testflows

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import org.slf4j.LoggerFactory

class TestSubflow : SubFlow<MemberX500Name> {

    companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }
    @CordaInject
    lateinit var flowEngine: FlowEngine

    @Suspendable
    override fun call(): MemberX500Name {
        log.info("TestSubflow.call()")
        return flowEngine.virtualNodeName
    }
}