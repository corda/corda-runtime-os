package net.corda.membership.impl.persistence.service.handler

import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.KeyValuePairList
import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.query.QueryRegistrationRequests
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.membership.datamodel.RegistrationRequestEntity
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.JpaEntitiesSet
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
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
import javax.persistence.criteria.Predicate
import javax.persistence.criteria.Root

class QueryRegistrationRequestsHandlerTest {
    private val holdingIdentity = HoldingIdentity("CN=Bob, O=Bob Corp, L=LDN, C=GB", "groupId")
    private val shortHash = holdingIdentity.toCorda().shortHash
    private val entitySet = mock<JpaEntitiesSet>()
    private val jpaEntitiesRegistry = mock<JpaEntitiesRegistry> {
        on { get(CordaDb.Vault.persistenceUnitName) } doReturn entitySet
    }
    private val entityTransaction = mock<EntityTransaction>()
    private val holdingIdentityShortHashPath = mock<Path<String>>()
    private val createdPath = mock<Path<Instant>>()
    private val root = mock<Root<RegistrationRequestEntity>> {
        on { get<String>("holdingIdentityShortHash") } doReturn holdingIdentityShortHashPath
        on { get<Instant>("created") } doReturn createdPath
    }
    private val predicate = mock<Predicate>()
    private val order = mock<Order>()
    private val query = mock<CriteriaQuery<RegistrationRequestEntity>> {
        on { from(RegistrationRequestEntity::class.java) } doReturn root
        on { select(root) } doReturn mock
        on { where(predicate) } doReturn mock
        on { orderBy(order) } doReturn mock
    }
    private val keyValuePairListDeserializer = mock<CordaAvroDeserializer<KeyValuePairList>> {
        on { deserialize(any()) } doReturn KeyValuePairList(emptyList())
    }
    private val serializationFactory = mock<CordaAvroSerializationFactory> {
        on { createAvroDeserializer(any(), eq(KeyValuePairList::class.java)) } doReturn keyValuePairListDeserializer
    }

    private val criteriaBuilder = mock<CriteriaBuilder> {
        on { createQuery(RegistrationRequestEntity::class.java) } doReturn query
        on { equal(holdingIdentityShortHashPath, shortHash.value) } doReturn predicate
        on { asc(createdPath) } doReturn order
    }
    private val actualQuery = mock<TypedQuery<RegistrationRequestEntity>>()
    private val entityManager = mock<EntityManager> {
        on { transaction } doReturn entityTransaction
        on { criteriaBuilder } doReturn criteriaBuilder
        on { createQuery(query) } doReturn actualQuery
    }
    private val entityManagerFactory = mock<EntityManagerFactory> {
        on { createEntityManager() } doReturn entityManager
    }
    private val dbConnectionManager = mock<DbConnectionManager> {
        on { createEntityManagerFactory(any(), any()) } doReturn entityManagerFactory
    }
    private val nodeInfo = mock<VirtualNodeInfo> {
        on { vaultDmlConnectionId } doReturn UUID(0, 0)
    }
    private val virtualNodeInfoReadService = mock<VirtualNodeInfoReadService> {
        on { getByHoldingIdentityShortHash(holdingIdentity.toCorda().shortHash) } doReturn nodeInfo
    }
    private val service = mock<PersistenceHandlerServices> {
        on { virtualNodeInfoReadService } doReturn virtualNodeInfoReadService
        on { dbConnectionManager } doReturn dbConnectionManager
        on { jpaEntitiesRegistry } doReturn jpaEntitiesRegistry
        on { cordaAvroSerializationFactory } doReturn serializationFactory
    }
    private val context = mock<MembershipRequestContext> {
        on { holdingIdentity } doReturn holdingIdentity
    }
    val request = QueryRegistrationRequests()

    private val handler = QueryRegistrationRequestsHandler(service)

    @Test
    fun `invoke return the correct response`() {
        val ids = (1..4).map {
            "id-$it"
        }
        whenever(actualQuery.resultList).doReturn(
            ids.map {
                RegistrationRequestEntity(
                    it,
                    shortHash.value,
                    "PENDING_MGM_NETWORK_ACCESS",
                    Instant.ofEpochSecond(500),
                    Instant.ofEpochSecond(600),
                    byteArrayOf(1, 2, 3)
                )
            }
        )

        val result = handler.invoke(context, request)

        assertThat(result.registrationRequests.map { it.registrationId })
            .containsAll(ids)
    }
}
