package net.corda.membership.impl.persistence.service.handler

import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.query.MutualTlsListAllowedCertificates
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.membership.datamodel.MutualTlsAllowedClientCertificateEntity
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.JpaEntitiesSet
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
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

class MutualTlsListAllowedCertificatesHandlerTest {
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
    private val entityTransaction = mock<EntityTransaction>()
    private val entityManager = mock<EntityManager> {
        on { transaction } doReturn entityTransaction
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
    private val persistenceHandlerServices = mock<PersistenceHandlerServices> {
        on { virtualNodeInfoReadService } doReturn virtualNodeInfoReadService
        on { dbConnectionManager } doReturn dbConnectionManager
        on { jpaEntitiesRegistry } doReturn jpaEntitiesRegistry
    }
    private val handler = MutualTlsListAllowedCertificatesHandler(persistenceHandlerServices)
    private val context = mock<MembershipRequestContext> {
        on { holdingIdentity } doReturn holdingIdentity
    }
    private val request = MutualTlsListAllowedCertificates()

    @Test
    fun `invoke will return all the subjects`() {
        val path = mock<Path<String>>()
        val isDeletedPath = mock<Path<Boolean>>()
        val order = mock<Order>()
        val root = mock<Root<MutualTlsAllowedClientCertificateEntity>> {
            on { get<String>("subject") } doReturn path
            on { get<Boolean>("isDeleted") } doReturn isDeletedPath
        }
        val equalsPredicate = mock<Predicate>()
        val criteriaQuery = mock<CriteriaQuery<MutualTlsAllowedClientCertificateEntity>> {
            on { from(MutualTlsAllowedClientCertificateEntity::class.java) } doReturn root
            on { select(root) } doReturn mock
            on { where(equalsPredicate) } doReturn mock
            on { orderBy(order) } doReturn mock
        }
        val criteriaBuilder = mock<CriteriaBuilder> {
            on { createQuery(MutualTlsAllowedClientCertificateEntity::class.java) } doReturn criteriaQuery
            on { asc(path) } doReturn order
            on { equal(isDeletedPath, false)} doReturn equalsPredicate
        }
        val query = mock<TypedQuery<MutualTlsAllowedClientCertificateEntity>> {
            on { resultList } doReturn
                    listOf("subject 1", "subject 2")
                        .map { MutualTlsAllowedClientCertificateEntity(it, false) }
        }
        whenever(entityManager.criteriaBuilder).doReturn(criteriaBuilder)
        whenever(entityManager.createQuery(criteriaQuery)).doReturn(query)

        val list = handler.invoke(
            context,
            request,
        )

        assertThat(list.subjects).containsExactly(
            "subject 1", "subject 2"
        )
    }
}