package net.corda.membership.impl.persistence.service.handler

import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.common.ApprovalRuleDetails
import net.corda.data.membership.common.ApprovalRuleType
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.query.QueryApprovalRules
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.membership.datamodel.ApprovalRulesEntity
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.JpaEntitiesSet
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
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
import javax.persistence.criteria.Path
import javax.persistence.criteria.Predicate
import javax.persistence.criteria.Root

class QueryApprovalRulesHandlerTest {
    private companion object {
        const val DUMMY_ID = "id"
        const val DUMMY_RULE = "corda.*"
        const val DUMMY_LABEL = "label1"
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
    private val mockEntity = mock<ApprovalRulesEntity> {
        on { ruleId } doReturn DUMMY_ID
        on { ruleRegex } doReturn DUMMY_RULE
        on { ruleType } doReturn ApprovalRuleType.STANDARD.name
        on { ruleLabel } doReturn DUMMY_LABEL
    }
    private val ruleTypePath = mock<Path<String>>()
    private val root = mock<Root<ApprovalRulesEntity>> {
        on { get<String>("ruleType") } doReturn ruleTypePath
    }
    private val predicate = mock<Predicate>()
    private val query = mock<CriteriaQuery<ApprovalRulesEntity>> {
        on { from(ApprovalRulesEntity::class.java) } doReturn root
        on { select(root) } doReturn mock
        on { where(predicate) } doReturn mock
    }
    private val criteriaBuilder = mock<CriteriaBuilder> {
        on { createQuery(ApprovalRulesEntity::class.java) } doReturn query
        on { equal(ruleTypePath, ApprovalRuleType.STANDARD.name) } doReturn predicate
        on { and(predicate, predicate) } doReturn predicate
    }
    private val approvalRulesQuery = mock<TypedQuery<ApprovalRulesEntity>>()
    private val entityManager = mock<EntityManager> {
        on { transaction } doReturn transaction
        on { criteriaBuilder } doReturn criteriaBuilder
        on { createQuery(query) } doReturn approvalRulesQuery
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
    private val persistenceHandlerServices = mock<PersistenceHandlerServices> {
        on { virtualNodeInfoReadService } doReturn nodeInfoReadService
        on { jpaEntitiesRegistry } doReturn registry
        on { dbConnectionManager } doReturn connectionManager
    }
    private val context = mock<MembershipRequestContext> {
        on { holdingIdentity } doReturn HoldingIdentity("CN=Bob, O=Bob Corp, L=LDN, C=GB", "group")
    }
    private val request = mock<QueryApprovalRules> {
        on { ruleType } doReturn ApprovalRuleType.STANDARD
    }
    private lateinit var handler: QueryApprovalRulesHandler

    @BeforeEach
    fun setUp() {
        handler = QueryApprovalRulesHandler(persistenceHandlerServices)
    }

    @Test
    fun `invoke returns rules as regular expressions`() {
        whenever(approvalRulesQuery.resultList).doReturn(listOf(mockEntity))
        val expectedRules = listOf(ApprovalRuleDetails(mockEntity.ruleId, mockEntity.ruleRegex, mockEntity.ruleLabel))

        val result = handler.invoke(context, request)

        assertThat(result.rules).isEqualTo(expectedRules)
    }

    @Test
    fun `invoke returns empty list if no rules are set`() {
        whenever(approvalRulesQuery.resultList).doReturn(emptyList())

        val result = handler.invoke(context, request)

        assertThat(result.rules).isEqualTo(emptyList<ApprovalRuleDetails>())
    }
}
