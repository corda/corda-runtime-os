package net.corda.simulator.runtime

import net.corda.simulator.HoldingIdentity
import net.corda.simulator.RequestData
import net.corda.simulator.SimulatedVirtualNode
import net.corda.simulator.crypto.HsmCategory
import net.corda.simulator.runtime.flows.FlowFactory
import net.corda.simulator.runtime.flows.FlowServicesInjector
import net.corda.simulator.runtime.messaging.SimFiber
import net.corda.simulator.runtime.signing.BaseSimKeyStore
import net.corda.simulator.runtime.signing.SimKeyStore
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.base.types.MemberX500Name
import java.security.PublicKey

class SimulatedVirtualNodeBase(
    override val holdingIdentity: HoldingIdentity,
    private val fiber: SimFiber,
    private val injector: FlowServicesInjector,
    private val flowFactory: FlowFactory,
    private val keyStore: SimKeyStore = BaseSimKeyStore()
) : SimulatedVirtualNode {

    override val member : MemberX500Name = holdingIdentity.member
    override fun callFlow(input: RequestData): String {
        val flowClassName = input.flowClassName
        val flow = flowFactory.createInitiatingFlow(member, flowClassName)
        injector.injectServices(flow, member, fiber, flowFactory, keyStore)
        return flow.call(input.toRPCRequestData())
    }

    override fun getPersistenceService(): PersistenceService =
        fiber.getOrCreatePersistenceService(member)

    override fun generateKey(alias: String, hsmCategory: HsmCategory, scheme: String) : PublicKey {
        val key = keyStore.generateKey(alias, hsmCategory, scheme)
        fiber.registerKey(member, key)
        return key
    }
}
