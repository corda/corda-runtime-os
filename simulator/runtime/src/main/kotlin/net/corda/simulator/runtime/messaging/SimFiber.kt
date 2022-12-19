package net.corda.simulator.runtime.messaging

import net.corda.simulator.SimulatorConfiguration
import net.corda.simulator.crypto.HsmCategory
import net.corda.simulator.runtime.flows.FlowServicesInjector
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.FlowContextProperties
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
interface SimFiber : Closeable, HasMemberInfos, FlowRegistry {

    /**
     * Registers an initiating member for [MemberLookup].
     */
    fun registerMember(member: MemberX500Name)

    /**
     * Gets the existing persistence service for the given member, or creates one if it does not exist.
     *
     * @param member The member for whom to create the persistence service.
     * @return The [PersistenceService] for the given member.
     */
    fun getOrCreatePersistenceService(member: MemberX500Name): PersistenceService

    /**
     * Creates a signing service for the member
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
     * @param contextProperties The [FlowContextProperties] for the flow.
     * @return A [FlowMessaging] services responsible for sending and receiving messages
     */
    fun createFlowMessaging(configuration: SimulatorConfiguration, flow: Flow, member: MemberX500Name,
                            injector: FlowServicesInjector, contextProperties: FlowContextProperties)
    : FlowMessaging

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
