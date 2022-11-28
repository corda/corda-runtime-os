package net.corda.simulator.runtime

import net.corda.simulator.HoldingIdentity
import net.corda.simulator.RequestData
import net.corda.simulator.SimulatedVirtualNode
import net.corda.simulator.crypto.HsmCategory
import net.corda.simulator.runtime.flows.FlowFactory
import net.corda.simulator.runtime.flows.FlowServicesInjector
import net.corda.simulator.runtime.messaging.SimFiber
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import java.security.PublicKey

/**
 * The base class for simulated virtual nodes.
 *
 * @property holdingIdentity The holding identity of the node.
 * @param fiber The simulated fiber / kafka bus on which shared state is stored.
 * @param injector The injector to initialize services on flows called in this node.
 * @param flowFactory A flow factory for creating called flows.
 * @param keyStore A store for generated keys.
 */
class SimulatedVirtualNodeBase(
    override val holdingIdentity: HoldingIdentity,
    private val fiber: SimFiber,
    private val injector: FlowServicesInjector,
    private val flowFactory: FlowFactory
) : SimulatedVirtualNode {

    companion object {
        val log = contextLogger()
    }

    override val member : MemberX500Name = holdingIdentity.member
    override fun callFlow(input: RequestData): String {
        val flowClassName = input.flowClassName
        log.info("Calling flow $flowClassName for member \"$member\" with request: ${input.requestBody}")
        val flow = flowFactory.createInitiatingFlow(member, flowClassName)
        injector.injectServices(flow, member, fiber, flowFactory)
        val result = flow.call(input.toRPCRequestData())
        log.info("Finished flow $flowClassName for member \"$member\"")
        return result
    }

    override fun callInstanceFlow(input: RequestData, flow: RPCStartableFlow): String {
        val flowClassName = input.flowClassName
        log.info("Calling flow $flowClassName for member \"$member\" with request: ${input.requestBody}")
        injector.injectServices(flow, member, fiber, flowFactory)
        val result = flow.call(input.toRPCRequestData())
        log.info("Finished flow $flowClassName for member \"$member\"")
        return result
    }


    override fun getPersistenceService(): PersistenceService =
        fiber.getOrCreatePersistenceService(member)

    override fun generateKey(alias: String, hsmCategory: HsmCategory, scheme: String) : PublicKey {
        log.info("Generating key with alias \"$alias\", hsm category \"$hsmCategory\", scheme \"$scheme\" " +
                "for member \"$member\""
        )
        return fiber.generateAndStoreKey(alias, hsmCategory, scheme, member)
    }
}
