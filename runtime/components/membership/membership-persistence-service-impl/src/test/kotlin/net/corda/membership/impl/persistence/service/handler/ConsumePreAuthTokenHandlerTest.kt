package net.corda.membership.impl.persistence.service.handler

import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.ConsumePreAuthToken
import net.corda.data.membership.preauth.PreAuthTokenStatus
import net.corda.data.membership.preauth.PreAuthTokenStatus.AUTO_INVALIDATED
import net.corda.data.membership.preauth.PreAuthTokenStatus.AVAILABLE
import net.corda.data.membership.preauth.PreAuthTokenStatus.CONSUMED
import net.corda.data.membership.preauth.PreAuthTokenStatus.REVOKED
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.membership.datamodel.PreAuthTokenEntity
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.orm.JpaEntitiesRegistry
import net.corda.test.util.time.TestClock
import net.corda.utilities.time.Clock
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityTransaction
import javax.persistence.LockModeType

class ConsumePreAuthTokenHandlerTest {

    private companion object {
        const val TEST_CREATION_REMARK = "token created"
    }

    private val ourGroupId = UUID(0, 1).toString()
    private val ourX500Name = MemberX500Name.parse("O=Alice,L=London,C=GB")
    private val ourHoldingIdentity = HoldingIdentity(ourX500Name, ourGroupId)
    private val ourVirtualNodeInfo: VirtualNodeInfo = mock {
        on { vaultDmlConnectionId } doReturn UUID(0, 1)
    }
    private val clock: Clock = TestClock(Instant.ofEpochSecond(1))

    private val tokenIdentifier = UUID(0, 1)
    private val tokenOwner = MemberX500Name.parse("O=Bob,L=London,C=GB")
    private val tokenTtl = clock.instant().plusSeconds(600)

    private val transaction: EntityTransaction = mock()
    private val em: EntityManager = mock {
        on { transaction } doReturn transaction
    }
    private val emf: EntityManagerFactory = mock {
        on { createEntityManager() } doReturn em
    }
    private val dbConnectionManager: DbConnectionManager = mock {
        on { createEntityManagerFactory(any(), any()) } doReturn emf
    }
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService = mock {
        on { getByHoldingIdentityShortHash(ourHoldingIdentity.shortHash) } doReturn ourVirtualNodeInfo
    }
    private val jpaEntitiesRegistry: JpaEntitiesRegistry = mock {
        on { get(any()) } doReturn mock()
    }

    private val persistenceHandlerServices: PersistenceHandlerServices = mock {
        on { clock } doReturn clock
        on { virtualNodeInfoReadService } doReturn virtualNodeInfoReadService
        on { dbConnectionManager } doReturn dbConnectionManager
        on { jpaEntitiesRegistry } doReturn jpaEntitiesRegistry
    }

    private val handler: ConsumePreAuthTokenHandler = ConsumePreAuthTokenHandler(persistenceHandlerServices)

    private val context = MembershipRequestContext(
        clock.instant(),
        UUID(0, 1).toString(),
        ourHoldingIdentity.toAvro()
    )

    private val request = ConsumePreAuthToken(
        tokenIdentifier.toString(),
        tokenOwner.toString()
    )

    private fun mockPersistedTokenEntity(entity: PreAuthTokenEntity) {
        whenever(
            em.find(
                PreAuthTokenEntity::class.java,
                tokenIdentifier.toString(),
                LockModeType.PESSIMISTIC_WRITE
            )
        ).doReturn(entity)
    }

    private fun createTokenEntity(
        id: String = tokenIdentifier.toString(),
        owner: String = tokenOwner.toString(),
        ttl: Instant? = tokenTtl,
        status: PreAuthTokenStatus = AVAILABLE,
        creationRemark: String = TEST_CREATION_REMARK
    ) = PreAuthTokenEntity(id, owner, ttl, status.toString(), creationRemark, null)

    private fun PreAuthTokenEntity.assertForEntityProperties(
        id: String = tokenIdentifier.toString(),
        owner: String = tokenOwner.toString(),
        ttl: Instant? = tokenTtl,
        status: PreAuthTokenStatus = AVAILABLE,
        creationRemark: String = TEST_CREATION_REMARK
    ) {
        assertThat(this.tokenId).isEqualTo(id)
        assertThat(this.ownerX500Name).isEqualTo(owner)
        assertThat(this.ttl).isEqualTo(ttl)
        assertThat(this.status).isEqualTo(status.toString())
        assertThat(this.creationRemark).isEqualTo(creationRemark)

        if (this.status == CONSUMED.toString()) {
            assertThat(this.removalRemark).isNotNull.contains("Token consumed")
        } else {
            assertThat(this.removalRemark).isNull()
        }
    }

    private fun invokeTestFunction() {
        handler.invoke(context, request)
    }

    private fun invokeTestFunctionWithError(errorMsg: String) {
        assertThrows<MembershipPersistenceException> {
            invokeTestFunction()
        }.apply {
            assertThat(message).contains(errorMsg)
        }
    }

    @AfterEach
    fun verify() {
        verify(em).find(
            PreAuthTokenEntity::class.java,
            tokenIdentifier.toString(),
            LockModeType.PESSIMISTIC_WRITE
        )
    }

    @Test
    fun `Handler can be called successfully and token is consumed`() {
        val entity = createTokenEntity()
        mockPersistedTokenEntity(entity)

        entity.assertForEntityProperties()

        invokeTestFunction()

        entity.assertForEntityProperties(status = CONSUMED)
        verify(em).merge(entity)
    }

    @Test
    fun `Handler can be called successfully with null TTL and token is consumed`() {
        val entity = createTokenEntity(ttl = null)
        mockPersistedTokenEntity(entity)

        entity.assertForEntityProperties(ttl = null)

        invokeTestFunction()

        entity.assertForEntityProperties(status = CONSUMED, ttl = null)
        verify(em).merge(entity)
    }

    @Test
    fun `Handler throws exception if no token is found for ID`() {
        invokeTestFunctionWithError("does not exist")
    }

    @Test
    fun `Handler throws exception if token is for different x500 name`() {
        mockPersistedTokenEntity(createTokenEntity(owner = "O=Fake,L=London,C=GB"))
        invokeTestFunctionWithError(tokenOwner.toString())
    }

    @Test
    fun `Handler throws exception if token TTL is expired`() {
        mockPersistedTokenEntity(createTokenEntity(ttl = clock.instant().minusSeconds(240)))
        invokeTestFunctionWithError("expired")
    }

    @Test
    fun `Handler throws exception if token is already consumed`() {
        mockPersistedTokenEntity(createTokenEntity(status = CONSUMED))
        invokeTestFunctionWithError("Status is $CONSUMED")
    }

    @Test
    fun `Handler throws exception if token is revoked`() {
        mockPersistedTokenEntity(createTokenEntity(status = REVOKED))
        invokeTestFunctionWithError("Status is $REVOKED")
    }

    @Test
    fun `Handler throws exception if token is invalidated`() {
        mockPersistedTokenEntity(createTokenEntity(status = AUTO_INVALIDATED))
        invokeTestFunctionWithError("Status is $AUTO_INVALIDATED")
    }
}