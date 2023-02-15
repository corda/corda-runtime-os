package net.corda.membership.impl.persistence.service.handler

import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.RevokePreAuthToken
import net.corda.data.membership.preauth.PreAuthTokenStatus
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.membership.datamodel.PreAuthTokenEntity
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.membership.mtls.allowed.list.service.AllowedCertificatesReaderWriterService
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.JpaEntitiesSet
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toCorda
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityTransaction
import javax.persistence.LockModeType

class RevokePreAuthTokenHandlerTest  {
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
    private val writerToKafka = mock<AllowedCertificatesReaderWriterService>()
    private val persistenceHandlerServices = mock<PersistenceHandlerServices> {
        on { virtualNodeInfoReadService } doReturn virtualNodeInfoReadService
        on { dbConnectionManager } doReturn dbConnectionManager
        on { jpaEntitiesRegistry } doReturn jpaEntitiesRegistry
        on { allowedCertificatesReaderWriterService } doReturn writerToKafka
    }
    private val handler = RevokePreAuthTokenHandler(persistenceHandlerServices)
    private val context = mock<MembershipRequestContext> {
        on { holdingIdentity } doReturn holdingIdentity
    }

    private fun mockPreAuthTokenEntity(entity: PreAuthTokenEntity?) {
        whenever(
            entityManager.find(
                PreAuthTokenEntity::class.java,
                TOKEN_ID,
                LockModeType.PESSIMISTIC_WRITE
            )
        ).thenReturn(entity)
    }

    @Test
    fun `invoke throws a MembershipPersistenceException if PreAuthTokenEntity cannot be found`() {
        val removalRemark = "Removed because"
        mockPreAuthTokenEntity(null)
        val mergedEntityCapture = argumentCaptor<PreAuthTokenEntity>()
        whenever(entityManager.merge(mergedEntityCapture.capture())).thenReturn(mock())

        assertThrows<MembershipPersistenceException> { handler.invoke(context, RevokePreAuthToken(TOKEN_ID, removalRemark)) }
    }

    @Test
    fun `invoke merges an AVAILABLE PreAuthTokenEntity with status REVOKED and a removal remark`() {
        val availableTokenEntity = PreAuthTokenEntity(
            TOKEN_ID,
            OWNER_X500_NAME,
            TTL,
            PreAuthTokenStatus.AVAILABLE.toString(),
            REMARK,
            null
        )
        mockPreAuthTokenEntity(availableTokenEntity)
        val removalRemark = "Removed because"
        val mergedEntityCapture = argumentCaptor<PreAuthTokenEntity>()
        whenever(entityManager.merge(mergedEntityCapture.capture())).thenReturn(mock())

        val tokenInResponse = handler.invoke(context, RevokePreAuthToken(TOKEN_ID, removalRemark)).preAuthToken

        val mergedEntity = mergedEntityCapture.allValues.single()
        SoftAssertions.assertSoftly {
            it.assertThat(mergedEntity.tokenId).isEqualTo(TOKEN_ID)
            it.assertThat(mergedEntity.ownerX500Name).isEqualTo(OWNER_X500_NAME)
            it.assertThat(mergedEntity.ttl).isEqualTo(TTL)
            it.assertThat(mergedEntity.status).isEqualTo(PreAuthTokenStatus.REVOKED.toString())
            it.assertThat(mergedEntity.creationRemark).isEqualTo(REMARK)
            it.assertThat(mergedEntity.removalRemark).isEqualTo(removalRemark)

            it.assertThat(tokenInResponse.id).isEqualTo(TOKEN_ID)
            it.assertThat(tokenInResponse.ownerX500Name).isEqualTo(OWNER_X500_NAME)
            it.assertThat(tokenInResponse.ttl).isEqualTo(TTL)
            it.assertThat(tokenInResponse.status).isEqualTo(PreAuthTokenStatus.REVOKED)
            it.assertThat(tokenInResponse.creationRemark).isEqualTo(REMARK)
            it.assertThat(tokenInResponse.removalRemark).isEqualTo(removalRemark)
        }
    }

    @Test
    fun `invoke throws a MembershipPersistenceException if PreAuthTokenEntity is already REVOKED`() {
        val removalRemark = "Removed because"
        val revokedTokenEntity = PreAuthTokenEntity(
            TOKEN_ID,
            OWNER_X500_NAME,
            TTL,
            PreAuthTokenStatus.REVOKED.toString(),
            REMARK,
            removalRemark
        )
        mockPreAuthTokenEntity(revokedTokenEntity)
        val mergedEntityCapture = argumentCaptor<PreAuthTokenEntity>()
        whenever(entityManager.merge(mergedEntityCapture.capture())).thenReturn(mock())

        assertThrows<MembershipPersistenceException> { handler.invoke(context, RevokePreAuthToken(TOKEN_ID, removalRemark)) }
    }

    @Test
    fun `invoke throws a MembershipPersistenceException if PreAuthTokenEntity is already AUTO_INVALIDATED`() {
        val removalRemark = "Invalidated because"
        val revokedTokenEntity = PreAuthTokenEntity(
            TOKEN_ID,
            OWNER_X500_NAME,
            TTL,
            PreAuthTokenStatus.AUTO_INVALIDATED.toString(),
            REMARK,
            removalRemark
        )
        mockPreAuthTokenEntity(revokedTokenEntity)
        val mergedEntityCapture = argumentCaptor<PreAuthTokenEntity>()
        whenever(entityManager.merge(mergedEntityCapture.capture())).thenReturn(mock())

        assertThrows<MembershipPersistenceException> { handler.invoke(context, RevokePreAuthToken(TOKEN_ID, removalRemark)) }
    }

    @Test
    fun `invoke throws a MembershipPersistenceException if PreAuthTokenEntity is already CONSUMED`() {
        val removalRemark = "Consumed because"
        val revokedTokenEntity = PreAuthTokenEntity(
            TOKEN_ID,
            OWNER_X500_NAME,
            TTL,
            PreAuthTokenStatus.CONSUMED.toString(),
            REMARK,
            removalRemark
        )
        mockPreAuthTokenEntity(revokedTokenEntity)
        val mergedEntityCapture = argumentCaptor<PreAuthTokenEntity>()
        whenever(entityManager.merge(mergedEntityCapture.capture())).thenReturn(mock())

        assertThrows<MembershipPersistenceException> { handler.invoke(context, RevokePreAuthToken(TOKEN_ID, removalRemark)) }
    }
}