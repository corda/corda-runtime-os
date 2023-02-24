package net.corda.membership.impl.persistence.service.handler

import net.corda.data.CordaAvroDeserializer
import net.corda.data.CordaAvroSerializationFactory
import net.corda.data.KeyValuePairList
import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.common.RegistrationStatus
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.query.QueryQueuedRegistrationRequests
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

class QueryQueuedRegistrationRequestHandlerTest {
    private val groupId = UUID.randomUUID().toString()
    private val viewOwner = HoldingIdentity("CN=Bob, O=Bob Corp, L=LDN, C=GB", groupId)

    private val memberShortHash = HoldingIdentity("CN=Alice, O=Alice Corp, L=LDN, C=GB", groupId).toCorda().shortHash

    private val nodeInfo: VirtualNodeInfo = mock {
        on { vaultDmlConnectionId } doReturn UUID(0, 0)
    }
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService = mock {
        on { getByHoldingIdentityShortHash(viewOwner.toCorda().shortHash) } doReturn nodeInfo
    }

    private val entityTransaction: EntityTransaction = mock()
    private val holdingIdentityShortHashPath: Path<String> = mock()
    private val statusPath: Path<String> = mock()
    private val predicate: Predicate = mock()
    private val createdPath: Path<Instant> = mock()
    private val order: Order = mock()
    private val root = mock<Root<RegistrationRequestEntity>> {
        on { get<String>("holdingIdentityShortHash") } doReturn holdingIdentityShortHashPath
        on { get<String>("status") } doReturn statusPath
        on { get<Instant>("created") } doReturn createdPath
    }
    private val query: CriteriaQuery<RegistrationRequestEntity> = mock {
        on { from(RegistrationRequestEntity::class.java) } doReturn root
        on { select(root) } doReturn mock
        on { where(any()) } doReturn mock
        on { orderBy(order) } doReturn mock
    }
    private val criteriaBuilder: CriteriaBuilder = mock {
        on { createQuery(RegistrationRequestEntity::class.java) } doReturn query
        on { equal(holdingIdentityShortHashPath, memberShortHash.value) } doReturn predicate
        on { equal(statusPath, RegistrationStatus.NEW.name) } doReturn predicate
        on { and(predicate, predicate) } doReturn predicate
        on { asc(createdPath) } doReturn order
    }
    private val actualQuery: TypedQuery<RegistrationRequestEntity> = mock()
    private val entityManager: EntityManager = mock {
        on { transaction } doReturn entityTransaction
        on { criteriaBuilder } doReturn criteriaBuilder
        on { createQuery(query) } doReturn actualQuery
    }
    private val entityManagerFactory: EntityManagerFactory = mock {
        on { createEntityManager() } doReturn entityManager
    }
    private val dbConnectionManager: DbConnectionManager = mock {
        on { createEntityManagerFactory(any(), any()) } doReturn entityManagerFactory
    }

    private val entitySet: JpaEntitiesSet = mock()
    private val jpaEntitiesRegistry: JpaEntitiesRegistry = mock {
        on { get(CordaDb.Vault.persistenceUnitName) } doReturn entitySet
    }

    private val keyValuePairListDeserializer: CordaAvroDeserializer<KeyValuePairList> = mock {
        on { deserialize(any()) } doReturn KeyValuePairList(emptyList())
    }
    private val serializationFactory: CordaAvroSerializationFactory = mock {
        on { createAvroDeserializer(any(), eq(KeyValuePairList::class.java)) } doReturn keyValuePairListDeserializer
    }

    private val service = mock<PersistenceHandlerServices> {
        on { virtualNodeInfoReadService } doReturn virtualNodeInfoReadService
        on { dbConnectionManager } doReturn dbConnectionManager
        on { jpaEntitiesRegistry } doReturn jpaEntitiesRegistry
        on { cordaAvroSerializationFactory } doReturn serializationFactory
    }

    private val handler = QueryQueuedRegistrationRequestHandler(service)

    private val context: MembershipRequestContext = mock {
        on { holdingIdentity } doReturn viewOwner
    }
    private val request: QueryQueuedRegistrationRequests = mock {
        on { requestSubjectShortHash } doReturn memberShortHash.value
    }

    @Test
    fun `invoke returns the oldest queued request`() {
        val ids = (1..4).map {
            "id-$it"
        }
        whenever(actualQuery.resultList).doReturn(
            (0..3).map {
                RegistrationRequestEntity(
                    ids[it],
                    memberShortHash.value,
                    "NEW",
                    Instant.ofEpochSecond(500 + it.toLong()),
                    Instant.ofEpochSecond(600),
                    byteArrayOf(1, 2, 3)
                )
            }
        )

        val result = handler.invoke(context, request)

        assertThat(result.registrationRequest.registrationId).isEqualTo("id-1")
    }

    @Test
    fun `invoke returns null when no queued requests were found`() {
        whenever(actualQuery.resultList).doReturn(
            emptyList()
        )

        val result = handler.invoke(context, request)

        assertThat(result.registrationRequest).isNull()
    }
}