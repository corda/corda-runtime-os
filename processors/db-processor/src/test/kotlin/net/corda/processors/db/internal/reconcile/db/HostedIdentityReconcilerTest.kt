package net.corda.processors.db.internal.reconcile.db

import net.corda.crypto.cipher.suite.KeyEncodingService
import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.core.CryptoTenants
import net.corda.crypto.core.ShortHash
import net.corda.data.crypto.wire.CryptoSigningKey
import net.corda.data.p2p.HostedIdentityEntry
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.membership.certificate.client.DbCertificateClient
import net.corda.membership.datamodel.HostedIdentityEntity
import net.corda.membership.datamodel.HostedIdentitySessionKeyInfoEntity
import net.corda.reconciliation.Reconciler
import net.corda.reconciliation.ReconcilerFactory
import net.corda.reconciliation.ReconcilerReader
import net.corda.reconciliation.ReconcilerWriter
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toAvro
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.nio.ByteBuffer
import java.security.PublicKey
import java.util.stream.Collectors
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityTransaction
import javax.persistence.criteria.CriteriaBuilder
import javax.persistence.criteria.CriteriaQuery
import javax.persistence.criteria.Path
import javax.persistence.criteria.Predicate
import javax.persistence.criteria.Root
import net.corda.crypto.client.ReconcilerCryptoOpsClient

class HostedIdentityReconcilerTest {
    private companion object {
        const val KNOWN_SESSION_KEY_STRING = "1234"
    }
    private val coordinator = mock<LifecycleCoordinator>()
    private val coordinatorFactory = mock<LifecycleCoordinatorFactory> {
        on { createCoordinator(any(), any()) } doReturn coordinator
    }
    private val knownHoldingId = HoldingIdentity(
        MemberX500Name.parse("C=GB, CN=Alice, O=Alice Corp, L=LDN"),
        "Group ID"
    )
    private val knownSessionKeyId = ShortHash.of("AB0123456789")
    private val knownPublicKey = mock<PublicKey> {
        on { encoded } doReturn KNOWN_SESSION_KEY_STRING.toByteArray()
    }
    private val virtualNodeInfo = mock<VirtualNodeInfo> {
        on { holdingIdentity } doReturn knownHoldingId
    }
    private val virtualNodeInfoReadService = mock<VirtualNodeInfoReadService> {
        on { getByHoldingIdentityShortHash(any()) } doReturn virtualNodeInfo
    }
    private val dbReader = argumentCaptor<DbReconcilerReader<String, HostedIdentityEntry>>()
    private val reconciler = mock<Reconciler>()
    private val kafkaReconcilerReader = mock<ReconcilerReader<String, HostedIdentityEntry>>()
    private val kafkaReconcilerWriter = mock<ReconcilerWriter<String, HostedIdentityEntry>>()
    private val chainCertificatesPems = this::class.java.getResource("/certificates/certificate.pem")!!
        .readText().replace("\r", "").replace("\n", System.lineSeparator())
    private val dbClient = mock<DbCertificateClient> {
        on { retrieveCertificates(anyOrNull(), any(), any()) } doReturn chainCertificatesPems
    }
    private val keyEncodingService = mock<KeyEncodingService> {
        on { encodeAsString(any()) } doReturn KNOWN_SESSION_KEY_STRING
        on { decodePublicKey(any<ByteArray>()) } doReturn knownPublicKey
    }
    private val cryptoSigningKey = mock<CryptoSigningKey> {
        on { publicKey } doReturn ByteBuffer.wrap(KNOWN_SESSION_KEY_STRING.toByteArray())
    }
    private val cryptoOpsClient = mock<ReconcilerCryptoOpsClient> {
        on { lookupKeysByIds(any(), any()) } doReturn listOf(cryptoSigningKey)
    }
    private val mockHostedIdentityEntity = mock<HostedIdentityEntity> {
        on { holdingIdentityShortHash } doReturn knownHoldingId.shortHash.value
        on { tlsCertificateChainAlias } doReturn "tls-certificate-alias"
        on { preferredSessionKeyAndCertificate } doReturn knownSessionKeyId.value
        on { version } doReturn 5
        on { useClusterLevelTlsCertificateAndKey } doReturn true
    }
    private val mockSessionKeyInfoEntity = mock<HostedIdentitySessionKeyInfoEntity> {
        on { holdingIdentityShortHash } doReturn knownHoldingId.shortHash.value
        on { sessionCertificateAlias } doReturn "session-certificate-alias"
        on { sessionKeyId } doReturn knownSessionKeyId.value
    }
    private val idPath = mock<Path<String>>()
    private val root = mock<Root<HostedIdentitySessionKeyInfoEntity>> {
        on { get<String>(any<String>()) } doReturn idPath
    }
    private val predicate = mock<Predicate>()
    private val identityQuery = mock<CriteriaQuery<HostedIdentityEntity>>()
    private val keyQuery = mock<CriteriaQuery<HostedIdentitySessionKeyInfoEntity>> {
        on { from(HostedIdentitySessionKeyInfoEntity::class.java) } doReturn root
        on { select(root) } doReturn mock
        on { where(predicate) } doReturn mock
    }
    private val criteriaBuilder = mock<CriteriaBuilder> {
        on { createQuery(HostedIdentityEntity::class.java) } doReturn identityQuery
        on { createQuery(HostedIdentitySessionKeyInfoEntity::class.java) } doReturn keyQuery
        on { equal(eq(idPath), any()) } doReturn predicate
    }
    private val transaction = mock<EntityTransaction>()
    private val entityManager = mock<EntityManager> {
        on { transaction } doReturn transaction
        on { criteriaBuilder } doReturn criteriaBuilder
        on { createQuery(eq(identityQuery)) } doAnswer {
            mock {
                on { resultList } doReturn listOf(mockHostedIdentityEntity)
            }
        }
        on { createQuery(eq(keyQuery)) } doAnswer {
            mock {
                on { resultList } doReturn listOf(mockSessionKeyInfoEntity)
            }
        }
    }
    private val entityManagerFactory = mock<EntityManagerFactory> {
        on { createEntityManager() } doReturn entityManager
    }
    private val dbConnectionManager = mock<DbConnectionManager> {
        on { getClusterEntityManagerFactory() } doReturn entityManagerFactory
    }
    private val reconcilerFactory = mock<ReconcilerFactory> {
        on {
            create(
                dbReader.capture(),
                eq(kafkaReconcilerReader),
                eq(kafkaReconcilerWriter),
                eq(String::class.java),
                eq(HostedIdentityEntry::class.java),
                any(),
                any(),
            )
        } doReturn reconciler
    }

    private val hostedIdentityReconciler = HostedIdentityReconciler(
        coordinatorFactory,
        dbConnectionManager,
        reconcilerFactory,
        kafkaReconcilerReader,
        kafkaReconcilerWriter,
        dbClient,
        cryptoOpsClient,
        keyEncodingService,
        virtualNodeInfoReadService,
        mock()
    )

    @Test
    fun `successfully build reader and reconciler`() {
        hostedIdentityReconciler.updateInterval(1000)

        assertThat(hostedIdentityReconciler.dbReconciler).isNotNull
        assertThat(hostedIdentityReconciler.reconciler).isNotNull
    }

    @Test
    fun `reader and reconciler are not rebuilt if they already exist`() {
        hostedIdentityReconciler.updateInterval(1000)
        val originalDbReconciler = hostedIdentityReconciler.dbReconciler
        verify(reconciler).start()

        hostedIdentityReconciler.updateInterval(1000)
        val currentDbReconciler = hostedIdentityReconciler.dbReconciler

        assertThat(originalDbReconciler).isEqualTo(currentDbReconciler)
        verify(reconciler).start()
        verify(reconciler).updateInterval(any())
    }

    @Test
    fun `get versioned records returns the expected result`() {
        hostedIdentityReconciler.updateInterval(1000)
        assertThat(hostedIdentityReconciler.dbReconciler).isNotNull

        val output = hostedIdentityReconciler.dbReconciler?.getAllVersionedRecords()

        assertThat(output).isNotNull
        val records = output?.collect(Collectors.toList())
        assertThat(records).isNotNull.hasSize(1)
        with(records!!.single()) {
            assertSoftly {
                it.assertThat(key).isEqualTo(knownHoldingId.shortHash.value)
                it.assertThat(value.holdingIdentity).isEqualTo(knownHoldingId.toAvro())
                it.assertThat(value.tlsCertificates).isEqualTo(listOf(chainCertificatesPems))
                it.assertThat(value.tlsTenantId).isEqualTo(CryptoTenants.P2P)
                val preferred = value.preferredSessionKeyAndCert
                it.assertThat(preferred.sessionPublicKey).isEqualTo(KNOWN_SESSION_KEY_STRING)
                it.assertThat(preferred.sessionCertificates).isEqualTo(listOf(chainCertificatesPems))
                it.assertThat(value.alternativeSessionKeysAndCerts).isEmpty()
                it.assertThat(value.version).isEqualTo(5)
            }
        }
    }
}
