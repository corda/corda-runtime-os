package net.corda.membership.impl.persistence.service.handler

import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.query.QueryMemberInfo
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.membership.datamodel.MemberInfoEntity
import net.corda.membership.datamodel.MemberInfoEntityPrimaryKey
import net.corda.membership.lib.MemberInfoFactory
import net.corda.orm.JpaEntitiesRegistry
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
import java.util.*
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityTransaction

class QueryMemberInfoHandlerTest {

    private val ourX500Name = MemberX500Name.parse("O=Alice,L=London,C=GB")
    private val otherX500Name = MemberX500Name.parse("O=Bob,L=London,C=GB")
    private val ourGroupId = UUID.randomUUID().toString()
    private val ourHoldingIdentity = HoldingIdentity(
        ourX500Name.toString(),
        ourGroupId
    )
    private val otherHoldingIdentity = HoldingIdentity(
        otherX500Name.toString(),
        ourGroupId
    )
    private val ourRegistrationId = UUID.randomUUID().toString()
    private val clock = TestClock(Instant.ofEpochSecond(0))

    private val vaultDmlConnectionId = UUID.randomUUID()
    private val cryptoDmlConnectionId = UUID.randomUUID()
    private val virtualNodeInfo = VirtualNodeInfo(
        ourHoldingIdentity,
        CpiIdentifier("TEST_CPI", "1.0", null),
        vaultDmlConnectionId = vaultDmlConnectionId,
        cryptoDmlConnectionId = cryptoDmlConnectionId,
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
        on { createEntityManagerFactory(eq(vaultDmlConnectionId), any()) } doReturn entityManagerFactory
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
        on { getById(eq(ourHoldingIdentity.id)) } doReturn virtualNodeInfo
    }

    private val services = PersistenceHandlerServices(
        clock,
        dbConnectionManager,
        jpaEntitiesRegistry,
        memberInfoFactory,
        cordaAvroSerializationFactory,
        virtualNodeInfoReadService
    )
    private lateinit var queryMemberInfoHandler: QueryMemberInfoHandler

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
    fun `invoke with no query identity does nothing`() {
        val result = queryMemberInfoHandler.invoke(
            getMemberRequestContext(),
            getQueryMemberInfo(emptyList())
        )

        assertThat(result.members).isEmpty()
        verify(entityManager, never()).find<MemberInfoEntity>(any(), any())
        verify(cordaAvroSerializationFactory, never()).createAvroDeserializer<KeyValuePairList>(any(), any())
        verify(keyValueDeserializer, never()).deserialize(any())
        verify(virtualNodeInfoReadService, never()).getById(any())
        verify(dbConnectionManager, never()).createEntityManagerFactory(any(), any())
        verify(jpaEntitiesRegistry, never()).get(any())
        verify(entityManagerFactory, never()).createEntityManager()
        verify(entityTransaction, never()).begin()
        verify(entityTransaction, never()).commit()
        verify(entityManager, never()).close()
    }

    @Test
    fun `invoke with a query identity returns results if results are available`() {
        whenever(
            entityManager.find(
                eq(MemberInfoEntity::class.java),
                eq(MemberInfoEntityPrimaryKey(ourGroupId, otherX500Name.toString()))
            )
        ).thenReturn(
            MemberInfoEntity(
                ourGroupId,
                otherX500Name.toString(),
                "OK",
                clock.instant(),
                memberContextBytes,
                mgmContextBytes,
                1L
            )
        )
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
        verify(entityManager).find(
            eq(MemberInfoEntity::class.java),
            eq(MemberInfoEntityPrimaryKey(ourGroupId, otherX500Name.toString()))
        )
        verify(cordaAvroSerializationFactory).createAvroDeserializer<KeyValuePairList>(any(), any())
        with(argumentCaptor<ByteArray>()) {
            verify(keyValueDeserializer, times(2)).deserialize(capture())
            assertThat(firstValue).isEqualTo(memberContextBytes)
            assertThat(secondValue).isEqualTo(mgmContextBytes)
        }
        with(argumentCaptor<String>()) {
            verify(virtualNodeInfoReadService).getById(capture())
            assertThat(firstValue).isEqualTo(ourHoldingIdentity.id)
        }
        verify(dbConnectionManager).createEntityManagerFactory(any(), any())
        verify(jpaEntitiesRegistry).get(any())
        verify(entityManagerFactory).createEntityManager()
        verify(entityTransaction).begin()
        verify(entityTransaction).commit()
        verify(entityManager).close()
    }

    @Test
    fun `invoke with a query identity returns no results if no results are available`() {
        whenever(
            entityManager.find(
                eq(MemberInfoEntity::class.java),
                eq(MemberInfoEntityPrimaryKey(ourGroupId, otherX500Name.toString()))
            )
        ).thenReturn(null)
        val result = queryMemberInfoHandler.invoke(
            getMemberRequestContext(),
            getQueryMemberInfo(listOf(otherHoldingIdentity))
        )
        assertThat(result.members).isEmpty()
        verify(entityManager).find(
            eq(MemberInfoEntity::class.java),
            eq(MemberInfoEntityPrimaryKey(ourGroupId, otherX500Name.toString()))
        )
        verify(cordaAvroSerializationFactory, never()).createAvroDeserializer<KeyValuePairList>(any(), any())
        verify(keyValueDeserializer, never()).deserialize(any())
        with(argumentCaptor<String>()) {
            verify(virtualNodeInfoReadService).getById(capture())
            assertThat(firstValue).isEqualTo(ourHoldingIdentity.id)
        }
        verify(dbConnectionManager).createEntityManagerFactory(any(), any())
        verify(jpaEntitiesRegistry).get(any())
        verify(entityManagerFactory).createEntityManager()
        verify(entityTransaction).begin()
        verify(entityTransaction).commit()
        verify(entityManager).close()
    }

}