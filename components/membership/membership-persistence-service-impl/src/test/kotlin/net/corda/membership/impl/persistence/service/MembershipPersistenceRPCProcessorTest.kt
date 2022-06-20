package net.corda.membership.impl.persistence.service

import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.db.request.MembershipPersistenceRequest
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.PersistMemberInfo
import net.corda.data.membership.db.request.command.PersistRegistrationRequest
import net.corda.data.membership.db.request.command.RegistrationStatus
import net.corda.data.membership.db.request.query.QueryMemberInfo
import net.corda.data.membership.db.response.MembershipPersistenceResponse
import net.corda.data.membership.db.response.query.MemberInfoQueryResponse
import net.corda.data.membership.db.response.query.QueryFailedResponse
import net.corda.data.membership.p2p.MembershipRegistrationRequest
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.membership.lib.MemberInfoFactory
import net.corda.orm.JpaEntitiesRegistry
import net.corda.test.util.time.TestClock
import net.corda.utilities.time.Clock
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.uncheckedCast
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import java.nio.ByteBuffer
import java.time.Instant
import java.util.*
import java.util.concurrent.CompletableFuture
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityTransaction

class MembershipPersistenceRPCProcessorTest {

    private lateinit var processor: MembershipPersistenceRPCProcessor

    private val clock: Clock = TestClock(Instant.now())

    private val requestId = UUID.randomUUID().toString()
    private val ourX500Name = MemberX500Name.parse("O=Alice, L=London, C=GB").toString()
    private val ourGroupId = UUID.randomUUID().toString()
    private val ourRegistrationId = UUID.randomUUID().toString()
    private val ourHoldingIdentity = HoldingIdentity(ourX500Name, ourGroupId)

    private val vaultDmlConnectionId = UUID.randomUUID()
    private val cryptoDmlConnectionId = UUID.randomUUID()
    private val virtualNodeInfo = VirtualNodeInfo(
        ourHoldingIdentity,
        CpiIdentifier("TEST_CPI", "1.0", null),
        vaultDmlConnectionId = vaultDmlConnectionId,
        cryptoDmlConnectionId = cryptoDmlConnectionId,
        timestamp = clock.instant()
    )

    private val entityTransaction: EntityTransaction = mock()
    private val entityManager: EntityManager = mock {
        on { transaction } doReturn entityTransaction
    }
    private val entityManagerFactory: EntityManagerFactory = mock {
        on { createEntityManager() } doReturn entityManager
    }

    private val dbConnectionManager: DbConnectionManager = mock {
        on { createEntityManagerFactory(eq(vaultDmlConnectionId), any()) } doReturn entityManagerFactory
    }
    private val jpaEntitiesRegistry: JpaEntitiesRegistry = mock {
        on { get(eq(CordaDb.Vault.persistenceUnitName)) } doReturn mock()
    }
    private val memberInfoFactory: MemberInfoFactory = mock()
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory = mock()
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService = mock {
        on { getById(eq(ourHoldingIdentity.id)) } doReturn virtualNodeInfo
    }

    private lateinit var responseFuture: CompletableFuture<MembershipPersistenceResponse>
    private lateinit var rqContext: MembershipRequestContext

    @BeforeEach
    fun setUp() {
        processor = MembershipPersistenceRPCProcessor(
            clock,
            dbConnectionManager,
            jpaEntitiesRegistry,
            memberInfoFactory,
            cordaAvroSerializationFactory,
            virtualNodeInfoReadService
        )
        responseFuture = CompletableFuture()
        rqContext = MembershipRequestContext(
            clock.instant(),
            requestId,
            ourHoldingIdentity.toAvro()
        )
    }

    @Test
    fun `unknown request type returns error`() {
        class UnknownRequest

        val rq = MembershipPersistenceRequest(rqContext, UnknownRequest())

        processor.onNext(rq, responseFuture)

        assertThat(responseFuture).isCompleted
        with(responseFuture.get()) {
            assertThat(payload).isNotNull
            assertThat(payload).isInstanceOf(QueryFailedResponse::class.java)

            with(context) {
                assertThat(requestTimestamp).isEqualTo(rqContext.requestTimestamp)
                assertThat(requestId).isEqualTo(rqContext.requestId)
                assertThat(responseTimestamp).isAfterOrEqualTo(rqContext.requestTimestamp)
                assertThat(holdingIdentity).isEqualTo(rqContext.holdingIdentity)
            }
        }
    }

    /**
     * Test verifies handler can be called. Handler has own test class testing specifics.
     */
    @Test
    fun `persist registration request returns success`() {
        val rq = MembershipPersistenceRequest(
            rqContext,
            PersistRegistrationRequest(
                RegistrationStatus.NEW,
                ourHoldingIdentity.toAvro(),
                MembershipRegistrationRequest(
                    ourRegistrationId,
                    ByteBuffer.wrap("context".toByteArray()),
                    CryptoSignatureWithKey(
                        ByteBuffer.wrap("123".toByteArray()),
                        ByteBuffer.wrap("456".toByteArray()),
                        KeyValuePairList(emptyList())
                    )
                )
            )
        )

        processor.onNext(rq, responseFuture)

        assertThat(responseFuture).isCompleted
        with(responseFuture.get()) {
            assertThat(payload).isNull()

            with(context) {
                assertThat(requestTimestamp).isEqualTo(rqContext.requestTimestamp)
                assertThat(requestId).isEqualTo(rqContext.requestId)
                assertThat(responseTimestamp).isAfterOrEqualTo(rqContext.requestTimestamp)
                assertThat(holdingIdentity).isEqualTo(rqContext.holdingIdentity)
            }
        }
    }

    /**
     * Test verifies handler can be called. Handler has own test class testing specifics.
     */
    @Test
    fun `persist member info returns success`() {
        val rq = MembershipPersistenceRequest(
            rqContext,
            PersistMemberInfo(emptyList())
        )

        processor.onNext(rq, responseFuture)

        assertThat(responseFuture).isCompleted
        with(responseFuture.get()) {
            assertThat(payload).isNull()

            with(context) {
                assertThat(requestTimestamp).isEqualTo(rqContext.requestTimestamp)
                assertThat(requestId).isEqualTo(rqContext.requestId)
                assertThat(responseTimestamp).isAfterOrEqualTo(rqContext.requestTimestamp)
                assertThat(holdingIdentity).isEqualTo(rqContext.holdingIdentity)
            }
        }
    }

    /**
     * Test verifies handler can be called. Handler has own test class testing specifics.
     */
    @Test
    fun `query member info returns success`() {
        val rq = MembershipPersistenceRequest(
            rqContext,
            QueryMemberInfo(emptyList())
        )

        processor.onNext(rq, responseFuture)

        assertThat(responseFuture).isCompleted
        with(responseFuture.get()) {
            assertThat(payload)
                .isInstanceOf(MemberInfoQueryResponse::class.java)
            assertThat((payload as MemberInfoQueryResponse).members)
                .isInstanceOf(List::class.java)
            assertThat(uncheckedCast<Any, List<PersistentMemberInfo>>((payload as MemberInfoQueryResponse).members))
                .isNotNull
                .isEmpty()

            with(context) {
                assertThat(requestTimestamp).isEqualTo(rqContext.requestTimestamp)
                assertThat(requestId).isEqualTo(rqContext.requestId)
                assertThat(responseTimestamp).isAfterOrEqualTo(rqContext.requestTimestamp)
                assertThat(holdingIdentity).isEqualTo(rqContext.holdingIdentity)
            }
        }
    }
}