package net.corda.membership.impl.persistence.service.handler

import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.CordaAvroSerializer
import net.corda.data.KeyValuePair
import net.corda.data.KeyValuePairList
import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.PersistGroupParameters
import net.corda.data.membership.db.response.command.PersistGroupParametersResponse
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.membership.datamodel.GroupParametersEntity
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.membership.lib.EPOCH_KEY
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.JpaEntitiesSet
import net.corda.test.util.time.TestClock
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toAvro
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityTransaction
import javax.persistence.TypedQuery
import javax.persistence.criteria.CriteriaBuilder
import javax.persistence.criteria.CriteriaQuery
import javax.persistence.criteria.Order
import javax.persistence.criteria.Path
import javax.persistence.criteria.Root
import kotlin.test.assertFailsWith

class PersistGroupParametersHandlerTest {
    private val context = byteArrayOf(1, 2, 3)
    private val keyValuePairListSerializer = mock<CordaAvroSerializer<KeyValuePairList>> {
        on { serialize(any()) } doReturn context
    }
    private val serializationFactory = mock<CordaAvroSerializationFactory> {
        on { createAvroSerializer<KeyValuePairList>(any()) } doReturn keyValuePairListSerializer
    }
    private val identity = HoldingIdentity("CN=Alice, O=Alice Corp, L=LDN, C=GB", "group").toCorda()
    private val vaultDmlConnectionId = UUID(1, 2)
    private val nodeInfo = mock<VirtualNodeInfo> {
        on { holdingIdentity } doReturn identity
        on { vaultDmlConnectionId } doReturn vaultDmlConnectionId
    }
    private val nodeInfoReadService = mock<VirtualNodeInfoReadService> {
        on { getByHoldingIdentityShortHash(any()) } doReturn nodeInfo
    }
    private val entitySet = mock<JpaEntitiesSet>()
    private val registry = mock<JpaEntitiesRegistry> {
        on { get(CordaDb.Vault.persistenceUnitName) } doReturn entitySet
    }
    private val transaction = mock<EntityTransaction>()
    private val resultList: List<GroupParametersEntity> = mock {
        on { isEmpty() } doReturn false
        on { size } doReturn 1
        on { singleOrNull() } doReturn GroupParametersEntity(1, "test".toByteArray())
    }
    private val previousEntry: TypedQuery<GroupParametersEntity> = mock {
        on { resultList } doReturn resultList
    }
    private val groupParametersQuery: TypedQuery<GroupParametersEntity> = mock {
        on { setMaxResults(1) } doReturn previousEntry
    }
    private val root = mock<Root<GroupParametersEntity>> {
        on { get<String>("epoch") } doReturn mock<Path<String>>()
    }
    private val order = mock<Order>()
    private val query = mock<CriteriaQuery<GroupParametersEntity>> {
        on { from(GroupParametersEntity::class.java) } doReturn root
        on { select(root) } doReturn mock
        on { orderBy(order) } doReturn mock
    }
    private val criteriaBuilder = mock<CriteriaBuilder> {
        on { createQuery(GroupParametersEntity::class.java) } doReturn query
        on { desc(any()) } doReturn order
    }
    private val entityManager = mock<EntityManager> {
        on { persist(any<GroupParametersEntity>()) } doAnswer {}
        on { criteriaBuilder } doReturn criteriaBuilder
        on { createQuery(eq(query)) } doReturn groupParametersQuery
        on { transaction } doReturn transaction
    }
    private val entityManagerFactory = mock<EntityManagerFactory> {
        on { createEntityManager() } doReturn entityManager
    }
    private val connectionManager = mock<DbConnectionManager> {
        on {
            createEntityManagerFactory(
                vaultDmlConnectionId,
                entitySet
            )
        } doReturn entityManagerFactory
    }
    private val clock = TestClock(Instant.ofEpochMilli(10))
    private val persistenceHandlerServices = mock<PersistenceHandlerServices> {
        on { cordaAvroSerializationFactory } doReturn serializationFactory
        on { virtualNodeInfoReadService } doReturn nodeInfoReadService
        on { jpaEntitiesRegistry } doReturn registry
        on { dbConnectionManager } doReturn connectionManager
        on { clock } doReturn clock
    }
    private val handler = PersistGroupParametersHandler(persistenceHandlerServices)
    private val mockGroupParameters = KeyValuePairList(
        listOf(
            KeyValuePair(EPOCH_KEY, "5")
        )
    )
    private val requestContext = mock<MembershipRequestContext> {
        on { holdingIdentity } doReturn identity.toAvro()
    }
    private val request = mock<PersistGroupParameters> {
        on { groupParameters } doReturn mockGroupParameters
    }

    @Test
    fun `invoke return the correct version`() {
        val result = handler.invoke(requestContext, request)
        assertThat(result).isEqualTo(PersistGroupParametersResponse(mockGroupParameters))
    }

    @Test
    fun `persisting group parameters is successful when there was nothing persisted previously`() {
        val previousEntry: TypedQuery<GroupParametersEntity> = mock {
            on { resultList } doReturn emptyList()
        }
        val groupParametersQuery: TypedQuery<GroupParametersEntity> = mock {
            on { setMaxResults(1) } doReturn previousEntry
        }
        whenever(entityManager.createQuery(eq(query))).doReturn(groupParametersQuery)

        val result = handler.invoke(requestContext, request)
        assertThat(result).isEqualTo(PersistGroupParametersResponse(mockGroupParameters))
    }

    @Test
    fun `invoke with lower epoch than previous group parameters throws exception`() {
        val request = mock<PersistGroupParameters> {
            on { groupParameters } doReturn KeyValuePairList(
                listOf(
                    KeyValuePair(EPOCH_KEY, "0")
                )
            )
        }

        val ex = assertFailsWith<MembershipPersistenceException> { handler.invoke(requestContext, request) }
        assertThat(ex.message).contains("already exist")
    }

    @Test
    fun `invoke throws exception when there is no epoch defined in request`() {
        val request = mock<PersistGroupParameters> {
            on { groupParameters } doReturn KeyValuePairList(emptyList())
        }

        val ex = assertFailsWith<MembershipPersistenceException> { handler.invoke(requestContext, request) }
        assertThat(ex.message).contains("epoch not found")
    }
}
