package net.corda.membership.impl.persistence.service.handler

import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.PersistHostedIdentity
import net.corda.data.membership.db.request.command.SessionKeyAndCertificate
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.membership.datamodel.HostedIdentityEntity
import net.corda.membership.datamodel.HostedIdentitySessionKeyInfoEntity
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.JpaEntitiesSet
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityTransaction
import javax.persistence.LockModeType
import javax.persistence.criteria.CriteriaBuilder
import javax.persistence.criteria.CriteriaDelete
import javax.persistence.criteria.Path
import javax.persistence.criteria.Predicate
import javax.persistence.criteria.Root

class PersistHostedIdentityHandlerTest {
    private val knownHoldingId = HoldingIdentity(
        MemberX500Name.parse("C=GB, CN=Alice, O=Alice Corp, L=LDN"),
        "Group ID"
    )
    private val transaction = mock<EntityTransaction>()
    private val mockHostedIdentityEntity = mock<HostedIdentityEntity> {
        on { holdingIdentityShortHash } doReturn knownHoldingId.shortHash.value
        on { tlsCertificateChainAlias } doReturn "tls-certificate-alias"
        on { preferredSessionKeyAndCertificate } doReturn "session-key-id"
        on { version } doReturn 5
        on { useClusterLevelTlsCertificateAndKey } doReturn true
    }
    private val idPath = mock<Path<String>>()
    private val root = mock<Root<HostedIdentitySessionKeyInfoEntity>> {
        on { get<String>(any<String>()) } doReturn idPath
    }
    private val predicate = mock<Predicate>()
    private val deleteQuery = mock<CriteriaDelete<HostedIdentitySessionKeyInfoEntity>> {
        on { from(HostedIdentitySessionKeyInfoEntity::class.java) } doReturn root
        on { where(predicate) } doReturn mock
    }
    private val criteriaBuilder = mock<CriteriaBuilder> {
        on { createCriteriaDelete(HostedIdentitySessionKeyInfoEntity::class.java) } doReturn deleteQuery
        on { equal(eq(idPath), any()) } doReturn predicate
    }
    private val entityManager = mock<EntityManager> {
        on { transaction } doReturn transaction
        on { criteriaBuilder } doReturn criteriaBuilder
        on { createQuery(deleteQuery) } doReturn mock()
        on { find(eq(HostedIdentityEntity::class.java), any(), any<LockModeType>()) } doReturn mockHostedIdentityEntity
    }
    private val entityManagerFactory = mock<EntityManagerFactory> {
        on { createEntityManager() } doReturn entityManager
    }
    private val entitySet = mock<JpaEntitiesSet>()
    private val registry = mock<JpaEntitiesRegistry> {
        on { get(CordaDb.Vault.persistenceUnitName) } doReturn entitySet
    }
    private val dbConnectionManager = mock<DbConnectionManager> {
        on { getClusterEntityManagerFactory() } doReturn entityManagerFactory
    }
    private val persistenceHandlerServices = mock<PersistenceHandlerServices> {
        on { jpaEntitiesRegistry } doReturn registry
        on { dbConnectionManager } doReturn dbConnectionManager
        on { transactionTimerFactory } doReturn { transactionTimer }
    }
    private val context = mock<MembershipRequestContext> {
        on { holdingIdentity } doReturn knownHoldingId.toAvro()
    }
    private val request = mock<PersistHostedIdentity> {
        on { sessionKeysAndCertificates } doAnswer {
            listOf(
                mock {
                    on { isPreferred } doReturn true
                    on { sessionKeyId } doReturn "test"
                }
            )
        }
        on { tlsCertificateAlias } doReturn "tls"
        on { useClusterLevelTls } doReturn true
    }

    private val handler = PersistHostedIdentityHandler(persistenceHandlerServices)

    @Test
    fun `invoke returns correct version on create`() {
        whenever(entityManager.find(eq(HostedIdentityEntity::class.java), any(), any<LockModeType>())).doReturn(null)
        val result = assertDoesNotThrow {
            handler.invoke(context, request)
        }

        assertThat(result.version).isEqualTo(1)
    }

    @Test
    fun `invoke returns correct version on update`() {
        val result = assertDoesNotThrow {
            handler.invoke(context, request)
        }

        assertThat(result.version).isEqualTo(mockHostedIdentityEntity.version + 1)
    }

    @Test
    fun `invoke deletes older session key info entries if hosted identity exists`() {
        assertDoesNotThrow {
            handler.invoke(context, request)
        }

        verify(entityManager).createQuery(any<CriteriaDelete<HostedIdentitySessionKeyInfoEntity>>())
    }

    @Test
    fun `invoke throws an exception if preferred session key info is not specified`() {
        whenever(request.sessionKeysAndCertificates).doReturn(emptyList())

        assertThrows<MembershipPersistenceException> {
            handler.invoke(context, request)
        }
    }

    @Test
    fun `invoke persists correct entities`() {
        val sessionKeyAndCert = listOf(
            SessionKeyAndCertificate("key-1", "cert-1", true),
            SessionKeyAndCertificate("key-2", "cert-2", false),
        )
        whenever(request.sessionKeysAndCertificates).doReturn(sessionKeyAndCert)
        val persistedHostedIdentity = argumentCaptor<HostedIdentityEntity>()
        whenever(entityManager.merge(persistedHostedIdentity.capture())).doReturn(mock())

        assertDoesNotThrow {
            handler.invoke(context, request)
        }

        verify(entityManager, times(2)).persist(any<HostedIdentitySessionKeyInfoEntity>())
        with(persistedHostedIdentity.firstValue) {
            assertSoftly {
                it.assertThat(holdingIdentityShortHash).isEqualTo(knownHoldingId.shortHash.value)
                it.assertThat(preferredSessionKeyAndCertificate).isEqualTo("key-1")
                it.assertThat(tlsCertificateChainAlias).isEqualTo(request.tlsCertificateAlias)
                it.assertThat(useClusterLevelTlsCertificateAndKey).isEqualTo(request.useClusterLevelTls)
                it.assertThat(version).isEqualTo(mockHostedIdentityEntity.version + 1)
            }
        }
    }
}
