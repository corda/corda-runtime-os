package net.corda.membership.impl.persistence.service.handler

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.core.ShortHash
import net.corda.data.crypto.wire.CryptoSignatureSpec
import net.corda.data.crypto.wire.CryptoSignatureWithKey
import net.corda.data.membership.PersistentMemberInfo
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.query.QueryMemberInfo
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.membership.datamodel.MemberInfoEntity
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_ACTIVE
import net.corda.membership.lib.MemberInfoExtension.Companion.MEMBER_STATUS_SUSPENDED
import net.corda.membership.lib.MemberInfoFactory
import net.corda.orm.JpaEntitiesRegistry
import net.corda.test.util.TestRandom
import net.corda.test.util.time.TestClock
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.ByteBuffer
import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityTransaction
import javax.persistence.TypedQuery
import javax.persistence.criteria.CriteriaBuilder
import javax.persistence.criteria.CriteriaQuery
import javax.persistence.criteria.Path
import javax.persistence.criteria.Root

class QueryMemberInfoHandlerTest {

    private val ourX500Name = MemberX500Name.parse("O=Alice,L=London,C=GB")
    private val otherX500Name = MemberX500Name.parse("O=Bob,L=London,C=GB")
    private val ourGroupId = UUID.randomUUID().toString()
    private val ourHoldingIdentity = HoldingIdentity(
        ourX500Name,
        ourGroupId
    )
    private val otherHoldingIdentity = HoldingIdentity(
        otherX500Name,
        ourGroupId
    )
    private val ourRegistrationId = UUID.randomUUID().toString()
    private val clock = TestClock(Instant.ofEpochSecond(0))
    private val vaultDmlConnectionId = UUID(44, 0)

    private val virtualNodeInfo = VirtualNodeInfo(
        ourHoldingIdentity,
        CpiIdentifier("TEST_CPI", "1.0", TestRandom.secureHash()),
        vaultDmlConnectionId = vaultDmlConnectionId,
        cryptoDmlConnectionId = UUID(0, 0),
        uniquenessDmlConnectionId = UUID(0, 0),
        timestamp = clock.instant()
    )

    private val memberContextBytes = "123".toByteArray()
    private val mgmContextBytes = "456".toByteArray()

    private val entityTransaction: EntityTransaction = mock()

    private val actualQuery = mock<TypedQuery<MemberInfoEntity>>()
    private val inHoldingId = mock<CriteriaBuilder.In<String>>()
    private val holdingIdPath = mock<Path<String>>()
    private val inStatus = mock<CriteriaBuilder.In<String>>()
    private val statusPath = mock<Path<String>>()
    private val root = mock<Root<MemberInfoEntity>> {
        on { get<String>("memberX500Name") } doReturn holdingIdPath
        on { get<String>("status") } doReturn statusPath
    }
    private val query = mock<CriteriaQuery<MemberInfoEntity>> {
        on { from(eq(MemberInfoEntity::class.java)) } doReturn root
        on { select(root) } doReturn mock
        on { where() } doReturn mock
        on { where(any()) } doReturn mock
        on { where(any(), any()) } doReturn mock
    }
    private val criteriaBuilder = mock<CriteriaBuilder> {
        on { createQuery(MemberInfoEntity::class.java) } doReturn query
        on { `in`(holdingIdPath) } doReturn inHoldingId
        on { `in`(statusPath) } doReturn inStatus
    }
    private val entityManager: EntityManager = mock {
        on { transaction } doReturn entityTransaction
        on { createQuery(eq(query)) } doReturn actualQuery
        on { criteriaBuilder } doReturn criteriaBuilder
    }
    private val entityManagerFactory: EntityManagerFactory = mock {
        on { createEntityManager() } doReturn entityManager
    }

    private val dbConnectionManager: DbConnectionManager = mock {
        on {
            createEntityManagerFactory(
                eq(vaultDmlConnectionId),
                any(),
            )
        } doReturn entityManagerFactory
    }
    private val jpaEntitiesRegistry: JpaEntitiesRegistry = mock {
        on { get(eq(CordaDb.Vault.persistenceUnitName)) } doReturn mock()
    }
    private val persistentInfo = mock<PersistentMemberInfo>()
    private val memberInfoFactory: MemberInfoFactory = mock {
        on {
            createPersistentMemberInfo(ourHoldingIdentity.toAvro(), memberContextBytes, mgmContextBytes)
        } doReturn persistentInfo
    }
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService = mock {
        on { getByHoldingIdentityShortHash(eq(ourHoldingIdentity.shortHash)) } doReturn virtualNodeInfo
    }
    private val keyEncodingService: KeyEncodingService = mock()
    private val platformInfoProvider: PlatformInfoProvider = mock()

    private val services = PersistenceHandlerServices(
        clock,
        dbConnectionManager,
        jpaEntitiesRegistry,
        memberInfoFactory,
        mock(),
        virtualNodeInfoReadService,
        keyEncodingService,
        platformInfoProvider,
        mock(),
    )
    private lateinit var queryMemberInfoHandler: QueryMemberInfoHandler

    private val memberInfoEntity = MemberInfoEntity(
        ourGroupId,
        otherX500Name.toString(),
        false,
        "OK",
        clock.instant(),
        memberContextBytes,
        "pk-$otherX500Name".toByteArray(),
        "sig-$otherX500Name".toByteArray(),
        "dummySignatureSpec",
        mgmContextBytes,
        1L
    )

    @BeforeEach
    fun setUp() {
        queryMemberInfoHandler = QueryMemberInfoHandler(services)
    }

    private fun getMemberRequestContext() = MembershipRequestContext(
        clock.instant(),
        ourRegistrationId,
        ourHoldingIdentity.toAvro(),
    )

    private fun getQueryMemberInfo(holdingIdentityFilter: List<HoldingIdentity>, statusFilter: List<String> = emptyList()) =
        QueryMemberInfo(holdingIdentityFilter.map { it.toAvro() }, statusFilter)


    @Test
    fun `invoke with no query identity returns results if results are available`() {
        whenever(actualQuery.resultList).thenReturn(listOf(memberInfoEntity))

        val requestContext = getMemberRequestContext()
        val result = queryMemberInfoHandler.invoke(
            requestContext,
            getQueryMemberInfo(emptyList())
        )

        assertThat(result.signedMembers).isNotEmpty.hasSize(1)
        with(result.signedMembers.first()) {
            assertThat(persistentMemberInfo).isEqualTo(persistentInfo)
            assertThat(memberSignature).isEqualTo(
                CryptoSignatureWithKey(
                    ByteBuffer.wrap(memberInfoEntity.memberSignatureKey),
                    ByteBuffer.wrap(memberInfoEntity.memberSignatureContent),
                )
            )
            assertThat(memberSignatureSpec).isEqualTo(
                CryptoSignatureSpec(memberInfoEntity.memberSignatureSpec, null, null)
            )
        }
        verify(inHoldingId, never()).value(any<String>())
        verify(inStatus, never()).value(any<String>())
        verify(entityManager, never()).find<MemberInfoEntity>(any(), any())
        with(argumentCaptor<ShortHash>()) {
            verify(virtualNodeInfoReadService).getByHoldingIdentityShortHash(capture())
            assertThat(firstValue).isEqualTo(ourHoldingIdentity.shortHash)
        }
        verify(jpaEntitiesRegistry).get(any())
        verify(entityManagerFactory).createEntityManager()
        verify(entityManagerFactory).close()
        verify(entityTransaction).begin()
        verify(entityTransaction).commit()
        verify(entityManager).close()
    }

    @Test
    fun `invoke with no query identity returns no results if no results are available`() {
        whenever(actualQuery.resultList).thenReturn(emptyList())

        val result = queryMemberInfoHandler.invoke(
            getMemberRequestContext(),
            getQueryMemberInfo(emptyList())
        )

        assertThat(result.signedMembers).isEmpty()
        verify(inHoldingId, never()).value(any<String>())
        verify(inStatus, never()).value(any<String>())
        verify(entityManager, never()).find<MemberInfoEntity>(any(), any())
        verify(memberInfoFactory, never()).createPersistentMemberInfo(any(), any(), any())
        with(argumentCaptor<ShortHash>()) {
            verify(virtualNodeInfoReadService).getByHoldingIdentityShortHash(capture())
            assertThat(firstValue).isEqualTo(ourHoldingIdentity.shortHash)
        }
        verify(jpaEntitiesRegistry).get(any())
        verify(entityManagerFactory).createEntityManager()
        verify(entityManagerFactory).close()
        verify(entityTransaction).begin()
        verify(entityTransaction).commit()
        verify(entityManager).close()
    }

    @Test
    fun `invoke with a query identity returns results if results are available`() {
        whenever(actualQuery.resultList).thenReturn(listOf(memberInfoEntity))

        val requestContext = getMemberRequestContext()
        val result = queryMemberInfoHandler.invoke(
            requestContext,
            getQueryMemberInfo(listOf(otherHoldingIdentity))
        )
        assertThat(result.signedMembers).isNotEmpty.hasSize(1)
        with(result.signedMembers.first()) {
            assertThat(persistentMemberInfo).isEqualTo(persistentInfo)
            assertThat(memberSignature).isEqualTo(
                CryptoSignatureWithKey(
                    ByteBuffer.wrap(memberInfoEntity.memberSignatureKey),
                    ByteBuffer.wrap(memberInfoEntity.memberSignatureContent),
                )
            )
            assertThat(memberSignatureSpec).isEqualTo(
                CryptoSignatureSpec(memberInfoEntity.memberSignatureSpec, null, null)
            )
        }
        with(argumentCaptor<ShortHash>()) {
            verify(virtualNodeInfoReadService).getByHoldingIdentityShortHash(capture())
            assertThat(firstValue).isEqualTo(ourHoldingIdentity.shortHash)
        }
        verify(inHoldingId).value(otherHoldingIdentity.x500Name.toString())
        verify(inStatus, never()).value(any<String>())
        verify(jpaEntitiesRegistry).get(any())
        verify(entityManagerFactory).createEntityManager()
        verify(entityManagerFactory).close()
        verify(entityTransaction).begin()
        verify(entityTransaction).commit()
        verify(entityManager).close()
    }

    @Test
    fun `invoke with a query identity returns no results if no results are available`() {
        whenever(actualQuery.resultList).thenReturn(emptyList())

        val result = queryMemberInfoHandler.invoke(
            getMemberRequestContext(),
            getQueryMemberInfo(listOf(otherHoldingIdentity))
        )
        assertThat(result.signedMembers).isEmpty()
        verify(inHoldingId).value(otherHoldingIdentity.x500Name.toString())
        verify(inStatus, never()).value(any<String>())
        verify(memberInfoFactory, never()).createPersistentMemberInfo(any(), any(), any())
        with(argumentCaptor<ShortHash>()) {
            verify(virtualNodeInfoReadService).getByHoldingIdentityShortHash(capture())
            assertThat(firstValue).isEqualTo(ourHoldingIdentity.shortHash)
        }
        verify(jpaEntitiesRegistry).get(any())
        verify(entityManagerFactory).createEntityManager()
        verify(entityManagerFactory).close()
        verify(entityTransaction).begin()
        verify(entityTransaction).commit()
        verify(entityManager).close()
    }

    @Test
    fun `invoke with query statuses returns results if results are available`() {
        whenever(actualQuery.resultList).thenReturn(listOf(memberInfoEntity))

        val requestContext = getMemberRequestContext()
        val result = queryMemberInfoHandler.invoke(
            requestContext,
            getQueryMemberInfo(emptyList(), listOf(MEMBER_STATUS_ACTIVE, MEMBER_STATUS_SUSPENDED))
        )
        assertThat(result.signedMembers).isNotEmpty.hasSize(1)
        with(result.signedMembers.first()) {
            assertThat(persistentMemberInfo).isEqualTo(persistentInfo)
            assertThat(memberSignature).isEqualTo(
                CryptoSignatureWithKey(
                    ByteBuffer.wrap(memberInfoEntity.memberSignatureKey),
                    ByteBuffer.wrap(memberInfoEntity.memberSignatureContent),
                )
            )
            assertThat(memberSignatureSpec).isEqualTo(
                CryptoSignatureSpec(memberInfoEntity.memberSignatureSpec, null, null)
            )
        }
        with(argumentCaptor<ShortHash>()) {
            verify(virtualNodeInfoReadService).getByHoldingIdentityShortHash(capture())
            assertThat(firstValue).isEqualTo(ourHoldingIdentity.shortHash)
        }
        verify(inStatus).value(MEMBER_STATUS_ACTIVE)
        verify(inStatus).value(MEMBER_STATUS_SUSPENDED)
        verify(inHoldingId, never()).value(any<String>())
        verify(jpaEntitiesRegistry).get(any())
        verify(entityManagerFactory).createEntityManager()
        verify(entityManagerFactory).close()
        verify(entityTransaction).begin()
        verify(entityTransaction).commit()
        verify(entityManager).close()
    }


    @Test
    fun `invoke with query identities and statuses returns results if results are available`() {
        whenever(actualQuery.resultList).thenReturn(listOf(memberInfoEntity))

        val requestContext = getMemberRequestContext()
        val result = queryMemberInfoHandler.invoke(
            requestContext,
            getQueryMemberInfo(listOf(otherHoldingIdentity), listOf(MEMBER_STATUS_ACTIVE, MEMBER_STATUS_SUSPENDED))
        )
        assertThat(result.signedMembers).isNotEmpty.hasSize(1)
        with(result.signedMembers.first()) {
            assertThat(persistentMemberInfo).isEqualTo(persistentInfo)
            assertThat(memberSignature).isEqualTo(
                CryptoSignatureWithKey(
                    ByteBuffer.wrap(memberInfoEntity.memberSignatureKey),
                    ByteBuffer.wrap(memberInfoEntity.memberSignatureContent),
                )
            )
            assertThat(memberSignatureSpec).isEqualTo(
                CryptoSignatureSpec(memberInfoEntity.memberSignatureSpec, null, null)
            )
        }
        with(argumentCaptor<ShortHash>()) {
            verify(virtualNodeInfoReadService).getByHoldingIdentityShortHash(capture())
            assertThat(firstValue).isEqualTo(ourHoldingIdentity.shortHash)
        }
        verify(inHoldingId).value(otherHoldingIdentity.x500Name.toString())
        verify(inStatus).value(MEMBER_STATUS_ACTIVE)
        verify(inStatus).value(MEMBER_STATUS_SUSPENDED)
        verify(jpaEntitiesRegistry).get(any())
        verify(entityManagerFactory).createEntityManager()
        verify(entityManagerFactory).close()
        verify(entityTransaction).begin()
        verify(entityTransaction).commit()
        verify(entityManager).close()
    }
}
