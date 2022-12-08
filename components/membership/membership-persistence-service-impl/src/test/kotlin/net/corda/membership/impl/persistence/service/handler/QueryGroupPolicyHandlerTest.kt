package net.corda.membership.impl.persistence.service.handler

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.KeyValuePairList
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.query.QueryGroupPolicy
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.platform.PlatformInfoProvider
import net.corda.membership.datamodel.GroupPolicyEntity
import net.corda.membership.lib.MemberInfoFactory
import net.corda.orm.JpaEntitiesRegistry
import net.corda.test.util.identity.createTestHoldingIdentity
import net.corda.test.util.time.TestClock
import net.corda.v5.base.types.MemberX500Name
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
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityTransaction
import javax.persistence.TypedQuery

class QueryGroupPolicyHandlerTest {
    private val x500Name = MemberX500Name.parse("O=MGM,L=London,C=GB").toString()
    private val groupId = UUID.randomUUID().toString()
    private val holdingIdentity = createTestHoldingIdentity(x500Name, groupId)

    private val clock = TestClock(Instant.ofEpochSecond(0))

    private val entityTransaction: EntityTransaction = mock()
    private val entityManager: EntityManager = mock {
        on { transaction } doReturn entityTransaction
    }
    private val entityManagerFactory: EntityManagerFactory = mock {
        on { createEntityManager() } doReturn entityManager
    }

    private val vaultDmlConnectionId = UUID(0, 33)
    private val dbConnectionManager: DbConnectionManager = mock {
        on { createEntityManagerFactory(
            eq(vaultDmlConnectionId),
            any()
        ) } doReturn entityManagerFactory
    }

    private val jpaEntitiesRegistry: JpaEntitiesRegistry = mock {
        on { get(eq(CordaDb.Vault.persistenceUnitName)) } doReturn mock()
    }

    private val memberInfoFactory: MemberInfoFactory = mock()

    private val groupPolicyBytes = "456".toByteArray()
    private val keyValuePairListDeserializer = mock<CordaAvroDeserializer<KeyValuePairList>>()
    private val cordaAvroSerializationFactory = mock<CordaAvroSerializationFactory> {
        on {
            createAvroDeserializer(
                any(),
                eq(KeyValuePairList::class.java)
            )
        } doReturn keyValuePairListDeserializer
    }

    private val virtualNodeInfo = VirtualNodeInfo(
        holdingIdentity,
        CpiIdentifier("TEST_CPI", "1.0", null),
        vaultDmlConnectionId = vaultDmlConnectionId,
        cryptoDmlConnectionId = UUID(0, 0),
        uniquenessDmlConnectionId = UUID(0, 0),
        timestamp = clock.instant()
    )
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService = mock {
        on { getByHoldingIdentityShortHash(eq(holdingIdentity.shortHash)) } doReturn virtualNodeInfo
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
    )

    private lateinit var queryGroupPolicyHandler: QueryGroupPolicyHandler

    @BeforeEach
    fun setUp() {
        queryGroupPolicyHandler = QueryGroupPolicyHandler(services)
    }

    private val groupPolicyQuery = mock<TypedQuery<GroupPolicyEntity>>()

    @Test
    fun `invoke returns the content of the group policy if available`() {
        whenever(entityManager.createQuery(any(), eq(GroupPolicyEntity::class.java))).thenReturn(groupPolicyQuery)
        whenever(groupPolicyQuery.resultList).thenReturn(listOf(GroupPolicyEntity(1, clock.instant(), groupPolicyBytes)))

        val result = queryGroupPolicyHandler.invoke(
            MembershipRequestContext(
                clock.instant(),
                UUID.randomUUID().toString(),
                holdingIdentity.toAvro()
            ),
            QueryGroupPolicy()
        )

        assertThat(result).isNotNull()
        verify(cordaAvroSerializationFactory).createAvroDeserializer(any(), eq(KeyValuePairList::class.java))
        verify(keyValuePairListDeserializer).deserialize(any())
    }

    @Test
    fun `invoke returns empty list if group policy is not available`() {
        whenever(entityManager.createQuery(any(), eq(GroupPolicyEntity::class.java))).thenReturn(groupPolicyQuery)
        whenever(groupPolicyQuery.resultList).thenReturn(emptyList())

        val result = queryGroupPolicyHandler.invoke(
            MembershipRequestContext(
                clock.instant(),
                UUID.randomUUID().toString(),
                holdingIdentity.toAvro()
            ),
            QueryGroupPolicy()
        )

        assertThat(result).isNotNull()
        assertThat(result.properties).isEqualTo(KeyValuePairList(emptyList()))
        verify(cordaAvroSerializationFactory, never()).createAvroDeserializer(any(), eq(KeyValuePairList::class.java))
        verify(keyValuePairListDeserializer, never()).deserialize(any())
    }
}
