package net.corda.membership.impl.persistence.service.handler

import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.query.QueryPreAuthToken
import net.corda.data.membership.preauth.PreAuthToken
import net.corda.data.membership.preauth.PreAuthTokenStatus
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.membership.datamodel.PreAuthTokenEntity
import net.corda.membership.mtls.allowed.list.service.AllowedCertificatesReaderWriterService
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.JpaEntitiesSet
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
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
import javax.persistence.criteria.CriteriaBuilder
import javax.persistence.criteria.CriteriaBuilder.In
import javax.persistence.criteria.CriteriaQuery
import javax.persistence.criteria.Expression
import javax.persistence.criteria.Path
import javax.persistence.criteria.Predicate
import javax.persistence.criteria.Root

class QueryPreAuthTokenHandlerTest {
    private companion object {
        const val TOKEN_ID = "tokenId"
        const val OWNER_X500_NAME = "x500Name"
        const val REMARK = "A remark"
        val TTL: Instant = Instant.ofEpochSecond(100)
    }
    private val holdingIdentity = HoldingIdentity(
        "CN=Mgm, O=Member ,L=London ,C=GB",
        "Group ID",
    )
    private val nodeInfo = mock<VirtualNodeInfo> {
        on { vaultDmlConnectionId } doReturn UUID(0, 90)
    }
    private val virtualNodeInfoReadService = mock<VirtualNodeInfoReadService> {
        on { getByHoldingIdentityShortHash(holdingIdentity.toCorda().shortHash) } doReturn nodeInfo
    }
    private val inStatus = mock<In<String>>()
    private val tokenIdPath = mock<Path<String>>()
    private val statusPath = mock<Path<String>>()
    private val ownerX500NamePath = mock<Path<String>>()
    private val root = mock<Root<PreAuthTokenEntity>> {
        on { get<String>("tokenId") } doReturn tokenIdPath
        on { get<String>("status") } doReturn statusPath
        on { get<String>("ownerX500Name") } doReturn ownerX500NamePath
    }
    private val tokenIdPredicate = mock<Predicate>()
    private val ownerX500NamePredicate = mock<Predicate>()
    private val query = mock<CriteriaQuery<PreAuthTokenEntity>> {
        on { from(PreAuthTokenEntity::class.java) } doReturn root
        on { select(root) } doReturn mock
        on { where() } doReturn mock
        on { where(any()) } doReturn mock
    }
    private val entityTransaction = mock<EntityTransaction>()
    private val actualQuery = mock<TypedQuery<PreAuthTokenEntity>>()
    private val criteriaBuilder = mock<CriteriaBuilder> {
        on { createQuery(PreAuthTokenEntity::class.java) } doReturn query
        on { equal(tokenIdPath, TOKEN_ID) } doReturn tokenIdPredicate
        on { equal(ownerX500NamePath, OWNER_X500_NAME) } doReturn ownerX500NamePredicate
        on { `in`(statusPath) } doReturn inStatus
    }
    private val entityManager = mock<EntityManager> {
        on { transaction } doReturn entityTransaction
        on { createQuery(query) } doReturn actualQuery
        on { criteriaBuilder } doReturn criteriaBuilder
    }
    private val entityManagerFactory = mock<EntityManagerFactory> {
        on { createEntityManager() } doReturn entityManager
    }
    private val dbConnectionManager = mock<DbConnectionManager> {
        on { createEntityManagerFactory(any(), any()) } doReturn entityManagerFactory
    }
    private val entitySet = mock<JpaEntitiesSet>()
    private val jpaEntitiesRegistry = mock<JpaEntitiesRegistry> {
        on { get(CordaDb.Vault.persistenceUnitName) } doReturn entitySet
    }
    private val writerToKafka = mock<AllowedCertificatesReaderWriterService>()
    private val persistenceHandlerServices = mock<PersistenceHandlerServices> {
        on { virtualNodeInfoReadService } doReturn virtualNodeInfoReadService
        on { dbConnectionManager } doReturn dbConnectionManager
        on { jpaEntitiesRegistry } doReturn jpaEntitiesRegistry
        on { allowedCertificatesReaderWriterService } doReturn writerToKafka
    }
    private val handler = QueryPreAuthTokenHandler(persistenceHandlerServices)
    private val context = mock<MembershipRequestContext> {
        on { holdingIdentity } doReturn holdingIdentity
    }

    @Test
    fun `invoke returns the correct response when entity was found`() {
        whenever(actualQuery.resultList).doReturn(
            listOf(PreAuthTokenEntity(TOKEN_ID, OWNER_X500_NAME, TTL, PreAuthTokenStatus.AVAILABLE.toString(), REMARK, null))
        )

        val result = handler.invoke(context, QueryPreAuthToken())

        Assertions.assertThat(result.tokens)
            .containsExactly(PreAuthToken(TOKEN_ID, OWNER_X500_NAME, TTL, PreAuthTokenStatus.AVAILABLE, REMARK, null))
    }

    @Test
    fun `invoke returns the correct response no entity was found`() {
        whenever(actualQuery.resultList).doReturn(emptyList())

        val result = handler.invoke(context, QueryPreAuthToken())

        Assertions.assertThat(result.tokens).isEmpty()
    }

    @Test
    fun `invoke queries with no predicates if nothing specified in request`() {
        whenever(actualQuery.resultList).doReturn(emptyList())

        handler.invoke(context, QueryPreAuthToken())

        verify(query).select(root)
        @Suppress("SpreadOperator")
        verify(query).where(*emptyArray())
    }

    @Test
    fun `invoke queries with correct predicate if ownerX500Name in request`() {
        whenever(actualQuery.resultList).doReturn(emptyList())
        val captor = argumentCaptor<Predicate>()
        whenever(query.where(captor.capture())).thenReturn(query)

        handler.invoke(context, QueryPreAuthToken(OWNER_X500_NAME, null, null))

        verify(query).select(root)
        Assertions.assertThat(captor.allValues.single()).isEqualTo(ownerX500NamePredicate)
    }

    @Test
    fun `invoke queries with correct predicate if tokenId in request`() {
        whenever(actualQuery.resultList).doReturn(emptyList())
        val captor = argumentCaptor<Predicate>()
        whenever(query.where(captor.capture())).thenReturn(query)

        handler.invoke(context, QueryPreAuthToken(null, TOKEN_ID, null))

        verify(query).select(root)
        Assertions.assertThat(captor.allValues.single()).isEqualTo(tokenIdPredicate)
    }

    @Test
    fun `invoke queries with correct predicate if empty statuses in request`() {
        whenever(actualQuery.resultList).doReturn(emptyList())
        val captor = argumentCaptor<Predicate>()
        whenever(query.where(captor.capture())).thenReturn(query)

        handler.invoke(context,
            QueryPreAuthToken(null, null, emptyList())
        )

        verify(query).select(root)
        Assertions.assertThat(captor.allValues.single()).isEqualTo(inStatus)
        verify(inStatus, never()).value(any<String>())
        verify(inStatus, never()).value(any<Expression<out String>>())
    }

    @Test
    fun `invoke queries with correct predicate if statuses in request`() {
        whenever(actualQuery.resultList).doReturn(emptyList())
        val captor = argumentCaptor<Predicate>()
        whenever(query.where(captor.capture())).thenReturn(query)

        handler.invoke(context,
            QueryPreAuthToken(null, null, listOf(PreAuthTokenStatus.REVOKED, PreAuthTokenStatus.AUTO_INVALIDATED))
        )

        verify(query).select(root)
        Assertions.assertThat(captor.allValues.single()).isEqualTo(inStatus)
        verify(inStatus).value(PreAuthTokenStatus.REVOKED.toString())
        verify(inStatus).value(PreAuthTokenStatus.AUTO_INVALIDATED.toString())
    }
}