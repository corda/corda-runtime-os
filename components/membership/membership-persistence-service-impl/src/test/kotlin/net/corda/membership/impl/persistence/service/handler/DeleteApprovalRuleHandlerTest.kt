package net.corda.membership.impl.persistence.service.handler

import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.common.ApprovalRuleType
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.DeleteApprovalRule
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.membership.datamodel.ApprovalRulesEntity
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.JpaEntitiesSet
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toCorda
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityTransaction

class DeleteApprovalRuleHandlerTest {
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
    private val entityManager = mock<EntityManager> {
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
    private val persistenceHandlerServices = mock<PersistenceHandlerServices> {
        on { virtualNodeInfoReadService } doReturn nodeInfoReadService
        on { jpaEntitiesRegistry } doReturn registry
        on { dbConnectionManager } doReturn connectionManager
    }
    private val context = mock<MembershipRequestContext> {
        on { holdingIdentity } doReturn HoldingIdentity("CN=Bob, O=Bob Corp, L=LDN, C=GB", "group")
    }
    private val request = mock<DeleteApprovalRule> {
        on { ruleId } doReturn DUMMY_ID
    }
    private lateinit var handler: DeleteApprovalRuleHandler

    @BeforeEach
    fun setUp() {
        handler = DeleteApprovalRuleHandler(persistenceHandlerServices)
    }

    @Test
    fun `invoke deletes rule`() {
        whenever(entityManager.find(eq(ApprovalRulesEntity::class.java), any())) doReturn mockEntity
        val mergedEntity = argumentCaptor<ApprovalRulesEntity>()
        doNothing().whenever(entityManager).remove(mergedEntity.capture())

        handler.invoke(context, request)

        with(mergedEntity.firstValue) {
            assertThat(ruleId)
                .isNotNull
                .isEqualTo(DUMMY_ID)
            assertThat(ruleRegex).isEqualTo(DUMMY_RULE)
            assertThat(ruleType).isEqualTo(ApprovalRuleType.STANDARD.toString())
            assertThat(ruleLabel).isEqualTo(DUMMY_LABEL)
        }
    }

    @Test
    fun `invoke throws exception if rule does not exist`() {
        whenever(entityManager.find(eq(ApprovalRulesEntity::class.java), any())) doReturn null

        assertThrows<MembershipPersistenceException> { handler.invoke(context, request) }
    }
}
