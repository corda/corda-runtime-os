package net.corda.simulator.runtime.messaging

import net.corda.simulator.runtime.config.DefaultConfigurationBuilder
import net.corda.simulator.runtime.flows.FlowServicesInjector
import net.corda.simulator.crypto.HsmCategory
import net.corda.simulator.runtime.persistence.CloseablePersistenceService
import net.corda.simulator.runtime.persistence.PersistenceServiceFactory
import net.corda.simulator.runtime.signing.KeyStoreFactory
import net.corda.simulator.runtime.signing.SigningServiceFactory
import net.corda.simulator.runtime.signing.SimKeyStore
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.types.MemberX500Name
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SimFiberBaseTest {

    private val memberA = MemberX500Name.parse("CN=CorDapperA, OU=Application, O=R3, L=London, C=GB")
    private val memberB = MemberX500Name.parse("CN=CorDapperB, OU=Application, O=R3, L=London, C=GB")

    @Test
    fun `should look up concrete implementations for a given protocol and a given party`() {
        // Given a fiber with a concrete implementation registered for a protocol
        val fiber = SimFiberBase()
        val flow = Flow1()
        fiber.registerFlowInstance(memberA, "protocol-1", flow)

        // When we look up an instance of a flow
        val result = fiber.lookUpResponderInstance(memberA, "protocol-1")

        // Then it should successfully return it
        assertThat(result, `is`(flow))
    }

    @Test
    fun `should look up the matching flow class for a given protocol and a given party`() {
        // Given a fiber and two nodes with some shared flow protocol
        val fiber = SimFiberBase()
        fiber.registerResponderClass(memberA, "protocol-1", Flow1::class.java)
        fiber.registerResponderClass(memberA, "protocol-2", Flow2::class.java)
        fiber.registerResponderClass(memberB, "protocol-1", Flow3InitBy1::class.java)
        fiber.registerResponderClass(memberB, "protocol-2", Flow4InitBy2::class.java)

        // When we look up a given protocol for a given party
        val flowClass = fiber.lookUpResponderClass(memberB, "protocol-2")

        // Then we should get the flow that matches both the protocol and the party
        assertThat(flowClass, `is`(Flow4InitBy2::class.java))
    }

    @Test
    fun `should prevent us from uploading a responder twice for a given party and protocol`() {
        // Given a fiber and a node with a flow and protocol already
        val fiber = SimFiberBase()
        fiber.registerResponderClass(memberA, "protocol-1", Flow1::class.java)
        fiber.registerFlowInstance(memberB, "protocol-1", Flow2())

        // When we try to register a node and flow with the same protocol
        // regardless of whether it is, or was, a class or instance
        // Then it should throw an error
        assertThrows<IllegalStateException> { // class, then class
            fiber.registerResponderClass(memberA, "protocol-1", Flow2::class.java)
        }
        assertThrows<IllegalStateException> { // instance, then class
            fiber.registerResponderClass(memberB, "protocol-1", Flow2::class.java)
        }
        assertThrows<IllegalStateException> { // instance, then instance
            fiber.registerFlowInstance(memberA, "protocol-1", Flow1())
        }
        assertThrows<IllegalStateException> { // class, then instance
            fiber.registerFlowInstance(memberA, "protocol-1", Flow1())
        }
    }

    @Test
    fun `should tell us if it cant find a flow for a given party and protocol`() {
        // Given a fiber and a node with a flow and protocol already
        val fiber = SimFiberBase()
        fiber.registerResponderClass(memberA, "protocol-1", Flow1::class.java)
        fiber.registerFlowInstance(memberB, "protocol-2", Flow2())

        // When we try to look up a member or protocol that doesn't exist
        // Then it should throw an error
        assertNull(fiber.lookUpResponderInstance(memberA, "protocol-2"))
        assertNull(fiber.lookUpResponderClass(memberA, "protocol-2"))

        assertNull(fiber.lookUpResponderInstance(memberB, "protocol-1"))
        assertNull(fiber.lookUpResponderClass(memberB, "protocol-1"))
    }

    @Test
    fun `should create a member lookup for a member`() {
        // Given a fiber
        val mlFactory = mock<MemberLookupFactory>()
        val fiber = SimFiberBase(memberLookUpFactory = mlFactory)

        // And some members who are going to be looked up
        val alice = MemberX500Name.parse("O=Alice, L=London, C=GB")
        fiber.registerMember(alice)

        // And a mock factory that will create a memberLookup for us
        val memberLookup = mock<MemberLookup>()
        whenever(mlFactory.createMemberLookup(any(), eq(fiber))).thenReturn(memberLookup)

        // When we create a member lookup
        fiber.createMemberLookup(alice)

        // Then it should use the factory to do it
        verify(mlFactory, times(1)).createMemberLookup(alice, fiber)
    }

    @Test
    fun `should create then retrieve the same persistence service for a member to avoid extra resources`() {
        val fiber = SimFiberBase()
        val persistenceService1 = fiber.getOrCreatePersistenceService(memberA)
        val persistenceService2 = fiber.getOrCreatePersistenceService(memberA)
        assertThat(persistenceService1, `is`(persistenceService2))
    }

    @Test
    fun `should close all persistence services when closed`() {
        // Given a mock factory that will create a persistence service for us
        val psFactory = mock<PersistenceServiceFactory>()
        val persistenceService = mock<CloseablePersistenceService>()
        whenever(psFactory.createPersistenceService(any())).thenReturn(persistenceService)

        // When we create a persistence service, then close the fiber
        val fiber = SimFiberBase(psFactory)
        fiber.getOrCreatePersistenceService(memberA)
        fiber.close()

        // Then it should have closed the persistence service too
        verify(persistenceService, times(1)).close()
    }

    @Test
    fun `should make generated keys available via MemberInfos`() {
        // Given a SimFiber
        val fiber = SimFiberBase()

        // With a member
        val member = MemberX500Name.parse("O=Alice, L=London, C=GB")
        fiber.registerMember(member)

        // When we get their memberInfos then the keys should be empty
        assertThat(fiber.members[member]?.ledgerKeys, `is`(listOf()))

        // When we generate a key then the memberInfo should also have it
        val key = fiber.generateAndStoreKey("my-key", HsmCategory.LEDGER, "any scheme", member)
        assertThat(fiber.members[member]?.ledgerKeys, `is`(listOf(key)))
    }

    @Test
    fun `should be able to create a signing service with the keystore for the given member`(){
        // Given a SimFiber which will create a signing service for our member
        val signingServiceFactory = mock<SigningServiceFactory>()
        val signingService = mock<SigningService>()
        val keyStoreFactory = mock<KeyStoreFactory>()
        val keyStore = mock<SimKeyStore>()
        val fiber = SimFiberBase(signingServiceFactory = signingServiceFactory, keystoreFactory = keyStoreFactory)

        whenever(keyStoreFactory.createKeyStore()).thenReturn(keyStore)
        whenever(signingServiceFactory.createSigningService(keyStore)).thenReturn(signingService)

        // When we register a member then create a signing service
        val member = MemberX500Name.parse("O=Alice, L=London, C=GB")
        fiber.registerMember(member)
        val createdService = fiber.createSigningService(member)

        // Then the signing service should have the keystore (it won't be created if it wasn't passed correctly)
        assertNotNull(createdService)
    }

    @Test
    fun `should throw an exception if an attempt to create a keystore is made for a member that was not registered`() {
        // Given a SimFiber which will create a signing service for our member
        val signingServiceFactory = mock<SigningServiceFactory>()
        val fiber = SimFiberBase(signingServiceFactory = signingServiceFactory)

        // When we create a signing service for a member that has not been registered (so no key store)
        // Then it should throw an exception
        val member = MemberX500Name.parse("O=Alice, L=London, C=GB")
        assertThrows<IllegalStateException> { fiber.createSigningService(member) }
    }


    @Test
    fun `should look up instance initiating flows for a given member`() {
        // Given a fiber with a concrete implementation registered for a protocol
        val fiber = SimFiberBase()
        val flow = mock<RPCStartableFlow>()
        val protocol = "protocol-1"
        fiber.registerFlowInstance(memberA, protocol, flow)

        // When we look up an instance of a flow
        val result = fiber.lookUpInitiatorInstance(memberA)

        // Then it should successfully return it
        assertThat(result, `is`(hashMapOf(flow to protocol)))
    }

    @Test
    fun `should create flow messaging for instance initiator flow`(){
        val flMsgFactory = mock<FlowMessagingFactory>()
        val fiber = SimFiberBase(flowMessagingFactory = flMsgFactory)

        // And some members who are going to be looked up
        val flow = mock<RPCStartableFlow>()
        val protocol = "protocol-1"
        fiber.registerFlowInstance(memberA, protocol, flow)

        val injector = mock<FlowServicesInjector>()
        val flowContext = FlowContext(DefaultConfigurationBuilder().build(), memberA, protocol)

        // And a mock factory that will create a memberLookup for us
        val flowMessaging = mock<FlowMessaging>()
        whenever(flMsgFactory.createFlowMessaging(any(), eq(fiber), any())).thenReturn(flowMessaging)

        // When we create a member lookup
        fiber.createFlowMessaging(flowContext.configuration, flow, memberA, injector)

        // Then it should use the factory to do it
        verify(flMsgFactory, times(1)).createFlowMessaging(flowContext, fiber, injector)
    }

    class Flow1 : ResponderFlow { override fun call(session: FlowSession) = Unit }
    class Flow2 : ResponderFlow { override fun call(session: FlowSession) = Unit }
    class Flow3InitBy1 : ResponderFlow { override fun call(session: FlowSession) = Unit }
    class Flow4InitBy2 : ResponderFlow { override fun call(session: FlowSession) = Unit }
}