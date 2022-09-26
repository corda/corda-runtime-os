package net.corda.simulator.runtime.messaging

import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.membership.MemberLookup
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
    fun registerInitiator(initiator: MemberX500Name)

    /**
     * Registers a responder class against the given member name and protocol.
     *
     * @param responder The member for whom to register the responder class.
     * @param protocol The detected protocol of the responder class.
     * @param flowClass The responder class to construct for the given protocol.
     */
    fun registerResponderClass(responder: MemberX500Name, protocol: String, flowClass: Class<out ResponderFlow>)

    /**
     * Registers an instance of a responder class against the given member name and protocol.
     *
     * @param responder The member for whom to register the responder class.
     * @param protocol The detected protocol of the responder class.
     * @param flowClass The instance of responder flow to use in response to the given protocol.
     */
    fun registerResponderInstance(responder: MemberX500Name, protocol: String, responderFlow: ResponderFlow)

    /**
     * @param member The member for whom to look up the responder class.
     * @param protocol The protocol to which the responder should respond.
     *
     * @return A [ResponderFlow] class previously registered against the given name and protocol, or null if
     * no class has been registered.
     */
    fun lookUpResponderClass(member: MemberX500Name, protocol: String): Class<out ResponderFlow>?

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
     * @param member The member for whom to create the persistence service
     * @return The [PersistenceService] for the given member.
     */
    fun getOrCreatePersistenceService(member: MemberX500Name): PersistenceService

    /**
     * Creates a member lookup as it exists at the time of calling.
     *
     * @param member The member for whom to create a member lookup.
     * @return A [MemberLookup] containing member details and a copy of any keys currently registered.
     */
    fun createMemberLookup(member: MemberX500Name): MemberLookup

    /**
     * @param member The member for whom to register a key.
     * @param publicKey The key to register.
     */
    fun registerKey(member: MemberX500Name, publicKey: PublicKey)
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
