package net.corda.cordapptestutils.internal

import net.corda.cordapptestutils.HoldingIdentity
import net.corda.cordapptestutils.crypto.HsmCategory
import net.corda.cordapptestutils.factories.RequestDataFactory
import net.corda.cordapptestutils.internal.flows.FlowFactory
import net.corda.cordapptestutils.internal.flows.FlowServicesInjector
import net.corda.cordapptestutils.internal.messaging.SimFiber
import net.corda.cordapptestutils.internal.signing.KeyStore
import net.corda.v5.application.flows.RPCStartableFlow
import net.corda.v5.application.persistence.PersistenceService
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.isA
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.security.KeyPairGenerator

class SimulatedVirtualNodeBaseTest {


    private lateinit var fiber : SimFiber
    private lateinit var flowFactory : FlowFactory
    private lateinit var injector : FlowServicesInjector
    private lateinit var keyStore : KeyStore
    private val holdingId = HoldingIdentity.create("IRunCordapps")

    @Test
    fun `should be a RequestDataFactory`() {
        assertThat(RPCRequestDataWrapperFactory(), isA(RequestDataFactory::class.java))
    }

    @BeforeEach
    fun `setup mocks`() {
        fiber = mock()
        flowFactory = mock()
        injector = mock()
        keyStore = mock()
    }

    @Test
    fun `should instantiate flow, inject services and call flow`() {
        // Given a virtual node with dependencies
        val flow = mock<RPCStartableFlow>()
        whenever(flowFactory.createInitiatingFlow(any(), any())).thenReturn(flow)

        val virtualNode = SimulatedVirtualNodeBase(
            holdingId,
            fiber,
            injector,
            flowFactory,
            keyStore
        )

        // When a flow class is run on that node
        // (NB: it doesn't actually matter if the flow class was created in that node or not)
        val input = RPCRequestDataWrapperFactory().create("r1", "aClass", "someData")
        virtualNode.callFlow(input)

        // Then it should have instantiated the node and injected the services into it
        verify(injector, times(1)).injectServices(eq(flow), eq(holdingId.member), eq(fiber), eq(flowFactory), any())

        // And the flow should have been called
        verify(flow, times(1)).call(argThat { request -> request.getRequestBody() == "someData" })
    }

    @Test
    fun `should return any persistence service registered for that member on the fiber`() {
        // Given a virtual node with dependencies
        val virtualNode = SimulatedVirtualNodeBase(
            holdingId,
            fiber,
            injector,
            flowFactory,
            keyStore
        )

        // And a persistence service registered on the fiber
        val persistenceService = mock<PersistenceService>()
        whenever(fiber.getOrCreatePersistenceService(holdingId.member)).thenReturn(persistenceService)

        // When we get the persistence service
        val result = virtualNode.getPersistenceService()

        // Then it should be the one that was registered
        assertThat(result, `is`(persistenceService))
    }

    @Test
    fun `should generate keys via the key store and register with the fiber`() {
        // Given a virtual node with dependencies
        val virtualNode = SimulatedVirtualNodeBase(
            holdingId,
            fiber,
            injector,
            flowFactory,
            keyStore
        )

        // And a key store which will give us a new key
        val publicKey = KeyPairGenerator.getInstance("RSA").generateKeyPair().public
        whenever(keyStore.generateKey("my-key", HsmCategory.LEDGER, "CORDA.ECDSA.SECP256R"))
            .thenReturn(publicKey)

        // When we create a new key
        val result = virtualNode.generateKey("my-key", HsmCategory.LEDGER, "CORDA.ECDSA.SECP256R")

        // Then it should pass it on to the key store
        assertThat(result, `is`(publicKey))

        // And the key should have been registered with the fiber
        verify(fiber, times(1)).registerKey(holdingId.member, publicKey)
    }
}