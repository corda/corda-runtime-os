package net.corda.simulator.runtime.messaging

import net.corda.simulator.crypto.HsmCategory
import net.corda.simulator.runtime.persistence.CloseablePersistenceService
import net.corda.simulator.runtime.persistence.PersistenceServiceFactory
import net.corda.simulator.runtime.signing.KeyStoreFactory
import net.corda.simulator.runtime.signing.SigningServiceFactory
import net.corda.simulator.runtime.signing.SimKeyStore
import net.corda.v5.application.crypto.SigningService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.base.types.MemberX500Name
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.Assertions.assertNotNull
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



}