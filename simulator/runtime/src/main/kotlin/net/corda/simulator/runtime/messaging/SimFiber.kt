package net.corda.simulator.runtime.messaging

import net.corda.simulator.SimulatorConfiguration
import net.corda.simulator.runtime.flows.FlowServicesInjector
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.simulator.crypto.HsmCategory
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.persistence.PersistenceService
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.membership.MemberInfo
import java.io.Closeable
import java.security.PublicKey

/**
 * Simulates the Quasar fiber and Kafka bus which sit at the centre of real Corda clusters. All state that is
 * shared between nodes, except for flow sessions, is stored on an instance of this class.
 */
interface SimFiber : Closeable, HasMemberInfos {

    /**
     * Registers an initiating member for [MemberLookup].
     */
    fun registerMember(member: MemberX500Name)

    /**
     * Registers a responder class against the given member name and protocol.
     *
     * @param responder The member for whom to register the responder class.
     * @param protocol The detected protocol of the responder class.
     * @param flowClass The responder class to construct for the given protocol.
     */
    fun registerResponderClass(responder: MemberX500Name, protocol: String, flowClass: Class<out ResponderFlow>)

    /**
     * Registers an instance initiating flows for a given member and protocol
     *
     * @param member The member who initiates/ responds to the flow
     * @param protocol The protocol of the initiating flow
     * @param instanceFlow The instance flow class
     */
    fun registerFlowInstance(member: MemberX500Name, protocol: String, instanceFlow: Flow)

    /**
     * @param member The member for whom to look up the responder class.
     * @param protocol The protocol to which the responder should respond.
     *
     * @return A [ResponderFlow] class previously registered against the given name and protocol, or null if
     * no class has been registered.
     */
    fun lookUpResponderClass(member: MemberX500Name, protocol: String): Class<out ResponderFlow>?

    /**
     * @param member The member for whom to look up the initiator instance.
     * @param protocol The protocol for the initiator flow.
     *
     * @return A [Map] of previously registered instance initiating flows with protocols
     */
    fun lookUpInitiatorInstance(member: MemberX500Name): Map<RPCStartableFlow, String>?

    /**
     * @param member The member for whom to look up the responder instance.
     * @param protocol The protocol to which the responder should respond.
     *
     * @return A [ResponderFlow] instance previously registered against the given name and protocol, or null if
     * no instance has been registered.
     */
    fun lookUpResponderInstance(member: MemberX500Name, protocol: String): ResponderFlow?

    /**
     * Gets the existing persistence service for the given member, or creates one if it does not exist.
     *
     * @param member The member for whom to create the persistence service.
     * @return The [PersistenceService] for the given member.
     */
    fun getOrCreatePersistenceService(member: MemberX500Name): PersistenceService

    /**
     * Gets the existing signing service for the given member, or creates one if it does not exist.
     *
     * @param member The member for whom to create the persistence service.
     * @return The [SigningService] for the given member.
     */
    fun createSigningService(member: MemberX500Name): SigningService

    /**
     * Creates a member lookup as it exists at the time of calling.
     *
     * @param member The member for whom to create a member lookup.
     * @return A [MemberLookup] containing member details and a copy of any keys currently registered.
     */
    fun createMemberLookup(member: MemberX500Name): MemberLookup

    /**
     * Creates a flow messing service.
     *
     * @param configuration for the Simulator
     * @param flow for which FlowMessaging is required
     * @param member The member for whom to create the FlowMessaging service
     * @param injector for flow services
     * @return A [FlowMessaging] services responsible for sending and receiving messages
     */
    fun createFlowMessaging(configuration: SimulatorConfiguration, flow: Flow,
                            member: MemberX500Name, injector: FlowServicesInjector): FlowMessaging

    /**
     * @param alias The alias to use for the key.
     * @param hsmCategory The HSM category for the key.
     * @param scheme The scheme for the key.
     * @param member The member for whom to register a key.
     * @return A generated key that is also registered with the member.
     */
    fun generateAndStoreKey(alias: String, hsmCategory: HsmCategory, scheme: String, member: MemberX500Name): PublicKey
}

/**
 * Exposes membership info.
 */
interface HasMemberInfos {
    /**
     * A map of registered members against their [MemberInfo].
     */
    val members: Map<MemberX500Name, MemberInfo>
}
