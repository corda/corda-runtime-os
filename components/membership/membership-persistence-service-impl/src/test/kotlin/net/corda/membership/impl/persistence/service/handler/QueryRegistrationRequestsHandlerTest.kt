package net.corda.membership.impl.persistence.service.handler

import net.corda.avro.serialization.CordaAvroDeserializer
import net.corda.avro.serialization.CordaAvroSerializationFactory
import net.corda.data.KeyValuePairList
import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.common.v2.RegistrationStatus
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
import org.mockito.kotlin.anyVararg
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
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
    private companion object {
        const val SERIAL = 0L
    }

    private val holdingIdentity = HoldingIdentity("CN=Bob, O=Bob Corp, L=LDN, C=GB", "groupId")
    private val shortHash = holdingIdentity.toCorda().shortHash
    private val entitySet = mock<JpaEntitiesSet>()
    private val jpaEntitiesRegistry = mock<JpaEntitiesRegistry> {
        on { get(CordaDb.Vault.persistenceUnitName) } doReturn entitySet
    }
    private val entityTransaction = mock<EntityTransaction>()
    private val inStatus = mock<CriteriaBuilder.In<String>>()
    private val statusPath = mock<Path<String>>()
    private val holdingIdentityShortHashPath = mock<Path<String>>()
    private val createdPath = mock<Path<Instant>>()
    private val root = mock<Root<RegistrationRequestEntity>> {
        on { get<String>("holdingIdentityShortHash") } doReturn holdingIdentityShortHashPath
        on { get<Instant>("created") } doReturn createdPath
        on { get<String>("status") } doReturn statusPath
    }
    private val predicate = mock<Predicate>()
    private val order = mock<Order>()
    private val query = mock<CriteriaQuery<RegistrationRequestEntity>> {
        on { from(RegistrationRequestEntity::class.java) } doReturn root
        on { select(any()) } doReturn mock
        on { where(anyVararg()) } doReturn mock
        on { orderBy(anyVararg<Order>()) } doReturn mock
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
        on { `in`(statusPath) } doReturn inStatus
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
        on { getOrCreateEntityManagerFactory(any<UUID>(), any()) } doReturn entityManagerFactory
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
        on { transactionTimerFactory } doReturn { transactionTimer }
    }
    private val context = MembershipRequestContext(Instant.ofEpochSecond(0), null, holdingIdentity)

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
                    "SENT_TO_MGM",
                    Instant.ofEpochSecond(500),
                    Instant.ofEpochSecond(600),
                    byteArrayOf(1, 2, 3),
                    byteArrayOf(4, 5),
                    byteArrayOf(6, 7),
                    "signatureSpec",
                    byteArrayOf(8, 9, 10),
                    byteArrayOf(11, 12),
                    byteArrayOf(13, 14),
                    "signatureSpec",
                    SERIAL,
                    "test reason"
                )
            }
        )

        val result =
            handler.invoke(context, QueryRegistrationRequests(null, RegistrationStatus.values().toList(), null))

        assertThat(result.registrationRequests.map { it.registrationId })
            .containsAll(ids)
    }

    @Test
    fun `invoke queries with correct predicates as per statuses specified in request`() {
        whenever(actualQuery.resultList).doReturn(emptyList())
        val captor = argumentCaptor<Predicate>()
        whenever(query.where(captor.capture())).thenReturn(query)

        handler.invoke(
            context,
            QueryRegistrationRequests(
                null,
                listOf(
                    RegistrationStatus.PENDING_MANUAL_APPROVAL,
                    RegistrationStatus.APPROVED,
                    RegistrationStatus.DECLINED
                ),
                null
            )
        )

        verify(query).select(root)
        assertThat(captor.allValues.single()).isEqualTo(inStatus)
        verify(inStatus).value(RegistrationStatus.PENDING_MANUAL_APPROVAL.toString())
        verify(inStatus).value(RegistrationStatus.APPROVED.toString())
        verify(inStatus).value(RegistrationStatus.DECLINED.toString())
    }

    @Test
    fun `invoke queries with correct predicates if X500 name specified in request`() {
        whenever(actualQuery.resultList).doReturn(emptyList())
        val captor = argumentCaptor<Predicate>()
        whenever(query.where(captor.capture(), any())).thenReturn(query)

        handler.invoke(
            context,
            QueryRegistrationRequests(
                holdingIdentity.x500Name, listOf(RegistrationStatus.PENDING_MANUAL_APPROVAL), null
            )
        )

        verify(query).select(root)
        assertThat(captor.allValues).contains(predicate)
    }

    @Test
    fun `invoke returns the sublist of the result when limit is specified in request`() {
        val ids = (1..4).map {
            "id-$it"
        }
        whenever(actualQuery.resultList).doReturn(
            ids.map {
                RegistrationRequestEntity(
                    it,
                    shortHash.value,
                    "SENT_TO_MGM",
                    Instant.ofEpochSecond(500),
                    Instant.ofEpochSecond(600),
                    byteArrayOf(1, 2, 3),
                    byteArrayOf(4, 5),
                    byteArrayOf(6, 7),
                    "signatureSpec",
                    byteArrayOf(8, 9, 10),
                    byteArrayOf(11, 12),
                    byteArrayOf(13, 14),
                    "signatureSpec",
                    SERIAL,
                )
            }
        )

        val result = handler.invoke(context, QueryRegistrationRequests(null, RegistrationStatus.values().toList(), 2))

        assertThat(result.registrationRequests.map { it.registrationId })
            .containsAll(ids.subList(0, 2))
        assertThat(result.registrationRequests).hasSize(2)
    }
}
