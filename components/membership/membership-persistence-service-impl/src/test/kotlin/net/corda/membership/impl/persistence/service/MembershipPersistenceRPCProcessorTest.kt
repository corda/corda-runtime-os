package net.corda.membership.impl.persistence.service

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.CordaAvroSerializer
import net.corda.data.KeyValuePairList
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.membership.common.ApprovalRuleDetails
import net.corda.data.membership.common.ApprovalRuleType
import net.corda.data.membership.common.RegistrationStatus
import net.corda.data.membership.db.request.MembershipPersistenceRequest
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.AddPreAuthToken
import net.corda.data.membership.db.request.command.ConsumePreAuthToken
import net.corda.data.membership.db.request.command.DeleteApprovalRule
import net.corda.data.membership.db.request.command.PersistApprovalRule
import net.corda.data.membership.db.request.command.PersistMemberInfo
import net.corda.data.membership.db.request.command.PersistRegistrationRequest
import net.corda.data.membership.db.request.command.RevokePreAuthToken
import net.corda.data.membership.db.request.command.UpdateRegistrationRequestStatus
import net.corda.data.membership.db.request.query.QueryApprovalRules
import net.corda.data.membership.db.request.query.QueryGroupPolicy
import net.corda.data.membership.db.request.query.QueryMemberInfo
import net.corda.data.membership.db.request.query.QueryPreAuthToken
import net.corda.data.membership.db.request.query.QueryRegistrationRequests
import net.corda.data.membership.db.response.MembershipPersistenceResponse
import net.corda.data.membership.db.response.command.DeleteApprovalRuleResponse
import net.corda.data.membership.db.response.command.PersistApprovalRuleResponse
import net.corda.data.membership.db.response.command.RevokePreAuthTokenResponse
import net.corda.data.membership.db.response.query.ApprovalRulesQueryResponse
import net.corda.data.membership.db.response.query.GroupPolicyQueryResponse
import net.corda.data.membership.db.response.query.MemberInfoQueryResponse
import net.corda.data.membership.db.response.query.PersistenceFailedResponse
import net.corda.data.membership.db.response.query.PreAuthTokenQueryResponse
import net.corda.data.membership.db.response.query.RegistrationRequestsQueryResponse
import net.corda.data.membership.p2p.MembershipRegistrationRequest
import net.corda.data.membership.preauth.PreAuthToken
import net.corda.data.membership.preauth.PreAuthTokenStatus
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.membership.datamodel.ApprovalRulesEntity
import net.corda.membership.datamodel.ApprovalRulesEntityPrimaryKey
import net.corda.membership.datamodel.GroupPolicyEntity
import net.corda.membership.datamodel.MemberInfoEntity
import net.corda.membership.datamodel.PreAuthTokenEntity
import net.corda.membership.datamodel.RegistrationRequestEntity
import net.corda.membership.lib.MemberInfoFactory
import net.corda.orm.JpaEntitiesRegistry
import net.corda.test.util.TestRandom
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.test.util.time.TestClock
import net.corda.utilities.time.Clock
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityTransaction
import javax.persistence.LockModeType
import javax.persistence.TypedQuery
import javax.persistence.criteria.CriteriaBuilder
import javax.persistence.criteria.CriteriaQuery
import javax.persistence.criteria.Order
import javax.persistence.criteria.Path
import javax.persistence.criteria.Predicate
import javax.persistence.criteria.Root

class MembershipPersistenceRPCProcessorTest {
    private companion object {
        const val DUMMY_ID = "rule-id"
        const val DUMMY_RULE = "corda.*"
        const val DUMMY_LABEL = "label1"
        const val SERIAL = 0L
    }

    private lateinit var processor: MembershipPersistenceRPCProcessor

    private val clock: Clock = TestClock(Instant.ofEpochSecond(0))

    private val requestId = UUID.randomUUID().toString()
    private val ourX500Name = MemberX500Name.parse("O=Alice, L=London, C=GB").toString()
    private val ourGroupId = UUID.randomUUID().toString()
    private val ourRegistrationId = UUID.randomUUID().toString()
    private val preAuthTokenId = UUID.randomUUID().toString()
    private val ourHoldingIdentity = createTestHoldingIdentity(ourX500Name, ourGroupId)
    private val context = "context".toByteArray()
    private val vaultDmlConnectionId = UUID(30, 0)

    private val virtualNodeInfo = VirtualNodeInfo(
        ourHoldingIdentity,
        CpiIdentifier("TEST_CPI", "1.0", TestRandom.secureHash()),
        timestamp = clock.instant(),
        vaultDmlConnectionId = vaultDmlConnectionId,
        cryptoDmlConnectionId = UUID(0, 0),
        uniquenessDmlConnectionId = UUID(0, 0)
    )

    private val registrationRequest = RegistrationRequestEntity(
        ourRegistrationId,
        ourHoldingIdentity.shortHash.value,
        RegistrationStatus.PENDING_MEMBER_VERIFICATION.name,
        clock.instant(),
        clock.instant(),
        context,
        SERIAL,
    )

    private val groupPolicyQuery: TypedQuery<GroupPolicyEntity> = mock {
        on { resultList } doReturn emptyList()
    }
    private val typedPreAuthTokenQuery: TypedQuery<PreAuthTokenEntity> = mock {
        on { resultList } doReturn emptyList()
    }
    private val preAuthTokenRoot = mock<Root<PreAuthTokenEntity>>()
    private val preAuthTokenQuery = mock<CriteriaQuery<PreAuthTokenEntity>> {
        on { from(PreAuthTokenEntity::class.java) } doReturn preAuthTokenRoot
        on { select(any()) } doReturn mock
        on { where() } doReturn mock
    }

    private val entityTransaction: EntityTransaction = mock()
    private val ruleTypePath = mock<Path<String>>()
    private val ruleRegexPath = mock<Path<String>>()
    private val root = mock<Root<ApprovalRulesEntity>> {
        on { get<String>("ruleType") } doReturn ruleTypePath
        on { get<String>("ruleRegex") } doReturn ruleRegexPath
    }
    private val predicate = mock<Predicate>()
    private val query = mock<CriteriaQuery<ApprovalRulesEntity>> {
        on { from(ApprovalRulesEntity::class.java) } doReturn root
        on { select(root) } doReturn mock
        on { where(predicate) } doReturn mock
    }
    private val inStatus = mock<CriteriaBuilder.In<String>>()
    private val statusPath = mock<Path<String>>()
    private val shortHashPath = mock<Path<String>>()
    private val createdPath = mock<Path<Instant>>()
    private val registrationRequestRoot = mock<Root<RegistrationRequestEntity>> {
        on { get<String>("status") } doReturn statusPath
        on { get<Instant>("created") } doReturn createdPath
        on { get<String>("holdingIdentityShortHash") } doReturn shortHashPath
    }
    private val order = mock<Order>()
    private val registrationRequestsQuery = mock<CriteriaQuery<RegistrationRequestEntity>> {
        on { from(RegistrationRequestEntity::class.java) } doReturn registrationRequestRoot
        on { select(registrationRequestRoot) } doReturn mock
        on { where() } doReturn mock
        on { where(any()) } doReturn mock
        on { orderBy(order) } doReturn mock
    }
    private val registrationRequestQuery = mock<TypedQuery<RegistrationRequestEntity>> {
        on { resultList } doReturn emptyList()
    }
    private val criteriaBuilder = mock<CriteriaBuilder> {
        on { createQuery(ApprovalRulesEntity::class.java) } doReturn query
        on { createQuery(PreAuthTokenEntity::class.java) } doReturn preAuthTokenQuery
        on { createQuery(RegistrationRequestEntity::class.java) } doReturn registrationRequestsQuery
        on { equal(ruleTypePath, ApprovalRuleType.STANDARD.name) } doReturn predicate
        on { equal(ruleRegexPath, DUMMY_RULE) } doReturn predicate
        on { and(predicate, predicate) } doReturn predicate
        on { `in`(statusPath) } doReturn inStatus
        on { asc(createdPath) } doReturn order
    }
    private val approvalRulesQuery = mock<TypedQuery<ApprovalRulesEntity>> {
        on { resultList } doReturn emptyList()
    }
    private val tokenTtl = Instant.now().plusSeconds(480)
    private val revokedAuthToken = PreAuthToken(
        UUID(0, 1).toString(),
        ourX500Name,
        tokenTtl,
        PreAuthTokenStatus.REVOKED,
        null,
        null
    )
    private val preAuthTokenEntity = PreAuthTokenEntity(
        UUID(0, 1).toString(),
        ourX500Name,
        tokenTtl,
        PreAuthTokenStatus.AVAILABLE.toString(),
        null,
        null
    )
    private val entityManager: EntityManager = mock {
        on { transaction } doReturn entityTransaction
        on { find(RegistrationRequestEntity::class.java, ourRegistrationId, LockModeType.PESSIMISTIC_WRITE) } doReturn registrationRequest
        on {
            find(PreAuthTokenEntity::class.java, preAuthTokenId, LockModeType.PESSIMISTIC_WRITE)
        } doReturn preAuthTokenEntity
        on { createQuery(any(), eq(GroupPolicyEntity::class.java)) } doReturn groupPolicyQuery
        on { criteriaBuilder } doReturn criteriaBuilder
        on { createQuery(query) } doReturn approvalRulesQuery
        on { createQuery(preAuthTokenQuery) } doReturn typedPreAuthTokenQuery
        on { merge(preAuthTokenEntity) } doReturn preAuthTokenEntity
        on { createQuery(registrationRequestsQuery) } doReturn registrationRequestQuery
    }
    private val entityManagerFactory: EntityManagerFactory = mock {
        on { createEntityManager() } doReturn entityManager
    }

    private val dbConnectionManager: DbConnectionManager = mock {
        on {
            createEntityManagerFactory(
                eq(vaultDmlConnectionId),
                any()
            )
        } doReturn entityManagerFactory
    }
    private val jpaEntitiesRegistry: JpaEntitiesRegistry = mock {
        on { get(eq(CordaDb.Vault.persistenceUnitName)) } doReturn mock()
    }
    private val memberInfoFactory: MemberInfoFactory = mock()
    private val keyValuePairListSerializer = mock<CordaAvroSerializer<KeyValuePairList>>()
    private val cordaAvroSerializationFactory = mock<CordaAvroSerializationFactory> {
        on { createAvroSerializer<KeyValuePairList>(any()) } doReturn keyValuePairListSerializer
    }
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService = mock {
        on { getByHoldingIdentityShortHash(eq(ourHoldingIdentity.shortHash)) } doReturn virtualNodeInfo
    }
    private val keyEncodingService: KeyEncodingService = mock()
    private val platformInfoProvider: PlatformInfoProvider = mock()

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
            virtualNodeInfoReadService,
            keyEncodingService,
            platformInfoProvider,
            mock(),
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

        assertSoftly {
            it.assertThat(responseFuture).isCompleted
            with(responseFuture.get()) {
                it.assertThat(payload).isNotNull
                it.assertThat(payload).isInstanceOf(PersistenceFailedResponse::class.java)

                with(context) {
                    it.assertThat(requestTimestamp).isEqualTo(rqContext.requestTimestamp)
                    it.assertThat(requestId).isEqualTo(rqContext.requestId)
                    it.assertThat(responseTimestamp).isAfterOrEqualTo(rqContext.requestTimestamp)
                    it.assertThat(holdingIdentity).isEqualTo(rqContext.holdingIdentity)
                }
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
                RegistrationStatus.PENDING_MEMBER_VERIFICATION,
                ourHoldingIdentity.toAvro(),
                MembershipRegistrationRequest(
                    ourRegistrationId,
                    ByteBuffer.wrap("8".toByteArray()),
                    CryptoSignatureWithKey(
                        ByteBuffer.wrap("123".toByteArray()),
                        ByteBuffer.wrap("456".toByteArray()),
                        KeyValuePairList(emptyList())
                    ),
                    true,
                    SERIAL,
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
        val memberInfoQuery = mock<TypedQuery<MemberInfoEntity>>()
        whenever(entityManager.createQuery(any(), eq(MemberInfoEntity::class.java))).thenReturn(memberInfoQuery)
        whenever(memberInfoQuery.resultList).thenReturn(emptyList())

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
                .isEmpty()

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
    fun `update registration request status return success`() {
        val rq = MembershipPersistenceRequest(
            rqContext,
            PersistRegistrationRequest(
                RegistrationStatus.PENDING_MEMBER_VERIFICATION,
                ourHoldingIdentity.toAvro(),
                MembershipRegistrationRequest(
                    ourRegistrationId,
                    ByteBuffer.wrap("8".toByteArray()),
                    CryptoSignatureWithKey(
                        ByteBuffer.wrap("123".toByteArray()),
                        ByteBuffer.wrap("456".toByteArray()),
                        KeyValuePairList(emptyList())
                    ),
                    false,
                    SERIAL,
                )
            )
        )

        processor.onNext(rq, responseFuture)

        assertThat(responseFuture).isCompleted
        responseFuture = CompletableFuture()

        val uRqContext = MembershipRequestContext(
            clock.instant(),
            UUID.randomUUID().toString(),
            ourHoldingIdentity.toAvro()
        )
        val uRq = MembershipPersistenceRequest(
            uRqContext,
            UpdateRegistrationRequestStatus(
                ourRegistrationId,
                RegistrationStatus.APPROVED,
                "test reason"
            )
        )

        processor.onNext(uRq, responseFuture)

        assertThat(responseFuture).isCompleted

        with(responseFuture.get()) {
            assertThat(payload).isNull()

            with(context) {
                assertThat(requestTimestamp).isEqualTo(uRqContext.requestTimestamp)
                assertThat(requestId).isEqualTo(uRqContext.requestId)
                assertThat(responseTimestamp).isAfterOrEqualTo(uRqContext.requestTimestamp)
                assertThat(holdingIdentity).isEqualTo(uRqContext.holdingIdentity)
            }
        }
    }

    @Test
    fun `query group policy return success`() {
        val rq = MembershipPersistenceRequest(
            rqContext,
            QueryGroupPolicy()
        )

        processor.onNext(rq, responseFuture)

        assertThat(responseFuture).isCompleted
        with(responseFuture.get()) {
            assertThat(payload).isNotNull
            assertThat(payload).isInstanceOf(GroupPolicyQueryResponse::class.java)

            with(context) {
                assertThat(requestTimestamp).isEqualTo(rqContext.requestTimestamp)
                assertThat(requestId).isEqualTo(rqContext.requestId)
                assertThat(responseTimestamp).isAfterOrEqualTo(rqContext.requestTimestamp)
                assertThat(holdingIdentity).isEqualTo(rqContext.holdingIdentity)
            }
        }
    }

    @Test
    fun `persist approval rule returns success`() {
        val rq = MembershipPersistenceRequest(
            rqContext,
            PersistApprovalRule(DUMMY_ID, DUMMY_RULE, ApprovalRuleType.STANDARD, DUMMY_LABEL)
        )

        processor.onNext(rq, responseFuture)

        assertThat(responseFuture).isCompleted
        with(responseFuture.get()) {
            assertThat(payload).isNotNull
            assertThat(payload).isInstanceOf(PersistApprovalRuleResponse::class.java)
            assertThat((payload as PersistApprovalRuleResponse).persistedRule)
                .isEqualTo(ApprovalRuleDetails(DUMMY_ID, DUMMY_RULE, DUMMY_LABEL))

            with(context) {
                assertThat(requestTimestamp).isEqualTo(rqContext.requestTimestamp)
                assertThat(requestId).isEqualTo(rqContext.requestId)
                assertThat(responseTimestamp).isAfterOrEqualTo(rqContext.requestTimestamp)
                assertThat(holdingIdentity).isEqualTo(rqContext.holdingIdentity)
            }
        }
    }

    @Test
    fun `delete approval rule returns success`() {
        whenever(
            entityManager.find(
                ApprovalRulesEntity::class.java,
                ApprovalRulesEntityPrimaryKey(DUMMY_ID, ApprovalRuleType.PREAUTH.name)
            )
        ).thenReturn(mock())
        val rq = MembershipPersistenceRequest(
            rqContext,
            DeleteApprovalRule(DUMMY_ID, ApprovalRuleType.PREAUTH)
        )

        processor.onNext(rq, responseFuture)

        assertThat(responseFuture).isCompleted
        with(responseFuture.get()) {
            assertThat(payload).isNotNull
            assertThat(payload).isInstanceOf(DeleteApprovalRuleResponse::class.java)

            with(context) {
                assertThat(requestTimestamp).isEqualTo(rqContext.requestTimestamp)
                assertThat(requestId).isEqualTo(rqContext.requestId)
                assertThat(responseTimestamp).isAfterOrEqualTo(rqContext.requestTimestamp)
                assertThat(holdingIdentity).isEqualTo(rqContext.holdingIdentity)
            }
        }
    }

    @Test
    fun `query approval rules returns success`() {
        val rq = MembershipPersistenceRequest(
            rqContext,
            QueryApprovalRules(ApprovalRuleType.STANDARD)
        )

        processor.onNext(rq, responseFuture)

        assertThat(responseFuture).isCompleted
        with(responseFuture.get()) {
            assertThat(payload).isNotNull
            assertThat(payload).isInstanceOf(ApprovalRulesQueryResponse::class.java)

            with(context) {
                assertThat(requestTimestamp).isEqualTo(rqContext.requestTimestamp)
                assertThat(requestId).isEqualTo(rqContext.requestId)
                assertThat(responseTimestamp).isAfterOrEqualTo(rqContext.requestTimestamp)
                assertThat(holdingIdentity).isEqualTo(rqContext.holdingIdentity)
            }
        }
    }

    @Test
    fun `query registration requests returns success`() {
        val rq = MembershipPersistenceRequest(
            rqContext,
            QueryRegistrationRequests(null, listOf(RegistrationStatus.PENDING_MANUAL_APPROVAL))
        )

        processor.onNext(rq, responseFuture)

        assertThat(responseFuture).isCompleted
        with(responseFuture.get()) {
            assertThat(payload).isNotNull
            assertThat(payload).isInstanceOf(RegistrationRequestsQueryResponse::class.java)

            with(context) {
                assertThat(requestTimestamp).isEqualTo(rqContext.requestTimestamp)
                assertThat(requestId).isEqualTo(rqContext.requestId)
                assertThat(responseTimestamp).isAfterOrEqualTo(rqContext.requestTimestamp)
                assertThat(holdingIdentity).isEqualTo(rqContext.holdingIdentity)
            }
        }
    }

    @Test
    fun `query pre auth token rules returns success`() {
        val rq = MembershipPersistenceRequest(
            rqContext,
            QueryPreAuthToken(null, null, null)
        )

        processor.onNext(rq, responseFuture)

        assertThat(responseFuture).isCompleted
        with(responseFuture.get()) {
            assertThat(payload).isNotNull
            assertThat(payload).isInstanceOf(PreAuthTokenQueryResponse::class.java)
            assertThat((payload as PreAuthTokenQueryResponse).tokens)
                .isEqualTo(emptyList<PreAuthToken>())

            with(context) {
                assertThat(requestTimestamp).isEqualTo(rqContext.requestTimestamp)
                assertThat(requestId).isEqualTo(rqContext.requestId)
                assertThat(responseTimestamp).isAfterOrEqualTo(rqContext.requestTimestamp)
                assertThat(holdingIdentity).isEqualTo(rqContext.holdingIdentity)
            }
        }
    }

    @Test
    fun `add pre auth token rules returns success`() {
        val rq = MembershipPersistenceRequest(
            rqContext,
            AddPreAuthToken("", "", Instant.ofEpochMilli(100), null)
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

    @Test
    fun `revoke pre auth token rules returns success`() {
        val rq = MembershipPersistenceRequest(
            rqContext,
            RevokePreAuthToken(preAuthTokenId, null)
        )

        processor.onNext(rq, responseFuture)

        assertThat(responseFuture).isCompleted
        with(responseFuture.get()) {
            assertThat(payload).isNotNull
            assertThat(payload).isInstanceOf(RevokePreAuthTokenResponse::class.java)
            assertThat((payload as RevokePreAuthTokenResponse).preAuthToken)
                .isEqualTo(revokedAuthToken)

            with(context) {
                assertThat(requestTimestamp).isEqualTo(rqContext.requestTimestamp)
                assertThat(requestId).isEqualTo(rqContext.requestId)
                assertThat(responseTimestamp).isAfterOrEqualTo(rqContext.requestTimestamp)
                assertThat(holdingIdentity).isEqualTo(rqContext.holdingIdentity)
            }
        }
    }

    @Test
    fun `consume pre auth token rules returns success`() {
        val rq = MembershipPersistenceRequest(
            rqContext,
            ConsumePreAuthToken(preAuthTokenId, ourX500Name)
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

}
