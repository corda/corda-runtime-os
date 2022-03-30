package net.corda.flowworker.development.flows

import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.flowservices.FlowEngine
import net.corda.v5.application.injection.CordaInject
import net.corda.v5.base.types.MemberX500Name

class TestGetNodeNameSubFlow : Flow<MemberX500Name> {

    @CordaInject
    lateinit var flowEngine: FlowEngine

    override fun call(): MemberX500Name {
        return flowEngine.virtualNodeName
    }
}