package net.corda.membership.impl.persistence.service.handler

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.core.ShortHash
import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.query.QueryMemberInfo
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.membership.datamodel.MemberInfoEntity
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
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityTransaction
import javax.persistence.TypedQuery

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
    private val testKey = "KEY"
    private val testMgmVal = "MGM"
    private val testMemberVal = "MEMBER"

    private val entityTransaction: EntityTransaction = mock()
    private val entityManager: EntityManager = mock {
        on { transaction } doReturn entityTransaction
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
    private val memberInfoFactory: MemberInfoFactory = mock()
    private val keyValueDeserializer: CordaAvroDeserializer<KeyValuePairList> = mock {
        on { deserialize(eq(memberContextBytes)) } doReturn KeyValuePairList(
            listOf(
                KeyValuePair(
                    testKey,
                    testMemberVal
                )
            )
        )
        on { deserialize(eq(mgmContextBytes)) } doReturn KeyValuePairList(listOf(KeyValuePair(testKey, testMgmVal)))
    }
    private val cordaAvroSerializationFactory: CordaAvroSerializationFactory = mock {
        on { createAvroDeserializer<KeyValuePairList>(any(), any()) } doReturn keyValueDeserializer
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
        cordaAvroSerializationFactory,
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

    private fun getQueryMemberInfo(queryIdentities: List<HoldingIdentity>) = QueryMemberInfo(
        queryIdentities.map { it.toAvro() }
    )


    @Test
    fun `invoke with no query identity returns results if results are available`() {
        val memberInfoQuery = mock<TypedQuery<MemberInfoEntity>>()
        whenever(entityManager.createQuery(any(), eq(MemberInfoEntity::class.java))).thenReturn(memberInfoQuery)
        whenever(memberInfoQuery.resultList).thenReturn(listOf(memberInfoEntity))

        val requestContext = getMemberRequestContext()
        val result = queryMemberInfoHandler.invoke(
            requestContext,
            getQueryMemberInfo(emptyList())
        )

        assertThat(result.members).isNotEmpty.hasSize(1)
        with(result.members.first()) {
            assertThat(viewOwningMember).isEqualTo(requestContext.holdingIdentity)
            assertThat(memberContext.items.first { it.key == testKey }.value).isEqualTo(testMemberVal)
            assertThat(mgmContext.items.first { it.key == testKey }.value).isEqualTo(testMgmVal)
        }
        verify(entityManager, never()).find<MemberInfoEntity>(any(), any())
        verify(entityManager).createQuery(any(), eq(MemberInfoEntity::class.java))
        verify(cordaAvroSerializationFactory).createAvroDeserializer<KeyValuePairList>(any(), any())
        with(argumentCaptor<ByteArray>()) {
            verify(keyValueDeserializer, times(2)).deserialize(capture())
            assertThat(firstValue).isEqualTo(memberContextBytes)
            assertThat(secondValue).isEqualTo(mgmContextBytes)
        }
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
        val memberInfoQuery = mock<TypedQuery<MemberInfoEntity>>()
        whenever(entityManager.createQuery(any(), eq(MemberInfoEntity::class.java))).thenReturn(memberInfoQuery)
        whenever(memberInfoQuery.resultList).thenReturn(emptyList())

        val result = queryMemberInfoHandler.invoke(
            getMemberRequestContext(),
            getQueryMemberInfo(emptyList())
        )

        assertThat(result.members).isEmpty()
        verify(entityManager, never()).find<MemberInfoEntity>(any(), any())
        verify(entityManager).createQuery(any(), eq(MemberInfoEntity::class.java))
        verify(cordaAvroSerializationFactory, never()).createAvroDeserializer<KeyValuePairList>(any(), any())
        verify(keyValueDeserializer, never()).deserialize(any())
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
        val memberInfoQuery = mock<TypedQuery<MemberInfoEntity>> {
            on { resultList } doReturn listOf(memberInfoEntity)
            on { setParameter(any<String>(), any()) } doReturn it
        }
        whenever(
            entityManager.createQuery(any(), eq(MemberInfoEntity::class.java))
        ).thenReturn(memberInfoQuery)
        val requestContext = getMemberRequestContext()
        val results = queryMemberInfoHandler.invoke(
            requestContext,
            getQueryMemberInfo(listOf(otherHoldingIdentity))
        )
        assertThat(results.members).isNotEmpty.hasSize(1)
        with(results.members.first()) {
            assertThat(viewOwningMember).isEqualTo(requestContext.holdingIdentity)
            assertThat(memberContext.items.first { it.key == testKey }.value).isEqualTo(testMemberVal)
            assertThat(mgmContext.items.first { it.key == testKey }.value).isEqualTo(testMgmVal)
        }
        verify(entityManager).createQuery(any(), eq(MemberInfoEntity::class.java))
        verify(cordaAvroSerializationFactory).createAvroDeserializer<KeyValuePairList>(any(), any())
        with(argumentCaptor<ByteArray>()) {
            verify(keyValueDeserializer, times(2)).deserialize(capture())
            assertThat(firstValue).isEqualTo(memberContextBytes)
            assertThat(secondValue).isEqualTo(mgmContextBytes)
        }
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
    fun `invoke with a query identity returns no results if no results are available`() {
        val memberInfoQuery = mock<TypedQuery<MemberInfoEntity>> {
            on { resultList } doReturn emptyList()
            on { setParameter(any<String>(), any()) } doReturn it
        }
        whenever(
            entityManager.createQuery(any(), eq(MemberInfoEntity::class.java))
        ).thenReturn(memberInfoQuery)
        val result = queryMemberInfoHandler.invoke(
            getMemberRequestContext(),
            getQueryMemberInfo(listOf(otherHoldingIdentity))
        )
        assertThat(result.members).isEmpty()
        verify(entityManager).createQuery(any(), eq(MemberInfoEntity::class.java))
        verify(cordaAvroSerializationFactory, never()).createAvroDeserializer<KeyValuePairList>(any(), any())
        verify(keyValueDeserializer, never()).deserialize(any())
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

}
