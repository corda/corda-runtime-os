package net.corda.cordapptestutils.internal

import net.corda.cordapptestutils.HoldingIdentity
import net.corda.cordapptestutils.RequestData
import net.corda.cordapptestutils.SimulatedVirtualNode
import net.corda.cordapptestutils.crypto.HsmCategory
import net.corda.cordapptestutils.internal.flows.FlowFactory
import net.corda.cordapptestutils.internal.flows.FlowServicesInjector
import net.corda.cordapptestutils.internal.messaging.SimFiber
import net.corda.cordapptestutils.internal.signing.BaseKeyStore
import net.corda.cordapptestutils.internal.signing.KeyStore
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.base.types.MemberX500Name
import java.security.PublicKey

class SimulatedVirtualNodeBase(
    override val holdingIdentity: HoldingIdentity,
    private val fiber: SimFiber,
    private val injector: FlowServicesInjector,
    private val flowFactory: FlowFactory,
    private val keyStore: KeyStore = BaseKeyStore()
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
