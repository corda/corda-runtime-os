package net.corda.membership.certificate.service.impl

import net.corda.crypto.core.ShortHash
import net.corda.data.certificates.CertificateUsage
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.membership.certificates.CertificateUsageUtils.publicName
import net.corda.membership.certificates.datamodel.Certificate
import net.corda.membership.certificates.datamodel.ClusterCertificate
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.JpaEntitiesSet
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityTransaction

class DbClientImplTest {
    private val mockTransaction = mock<EntityTransaction>()
    private val clusterEntityManager = mock<EntityManager> {
        on { transaction } doReturn mockTransaction
    }
    private val clusterFactory = mock<EntityManagerFactory> {
        on { createEntityManager() } doReturn clusterEntityManager
    }
    private val nodeEntityManager = mock<EntityManager> {
        on { transaction } doReturn mockTransaction
    }
    private val nodeFactory = mock<EntityManagerFactory> {
        on { createEntityManager() } doReturn nodeEntityManager
    }
    private val registry = mock<JpaEntitiesSet>()
    private val jpaEntitiesRegistry = mock<JpaEntitiesRegistry> {
        on { get(CordaDb.Vault.persistenceUnitName) } doReturn registry
    }
    private val nodeTenantId = ShortHash.of("1234567890ab")
    private val dmlConnectionId = UUID(10, 30)
    private val dbConnectionManager = mock<DbConnectionManager> {
        on { getClusterEntityManagerFactory() } doReturn clusterFactory
        on {
            createEntityManagerFactory(
                eq(dmlConnectionId),
                eq(registry)
            )
        } doReturn nodeFactory
    }
    private val nodeInfo = mock<VirtualNodeInfo> {
        on { vaultDmlConnectionId } doReturn dmlConnectionId
    }
    private val virtualNodeInfoReadService = mock<VirtualNodeInfoReadService> {
        on { getByHoldingIdentityShortHash(nodeTenantId) } doReturn nodeInfo
    }

    private val client = DbClientImpl(
        dbConnectionManager,
        jpaEntitiesRegistry,
        virtualNodeInfoReadService
    )

    @Test
    fun `importCertificates merge the certificate into the P2P_TLS tenant`() {
        client.importCertificates(
            CertificateUsage.P2P_TLS,
            null,
            "alias",
            "certificate"
        )

        verify(clusterEntityManager).merge(
            ClusterCertificate("alias", CertificateUsage.P2P_TLS.publicName, "certificate")
        )
    }

    @Test
    fun `importCertificates merge the certificate into the RPC API tenant`() {
        client.importCertificates(
            CertificateUsage.RPC_API_TLS,
            null,
            "alias",
            "certificate"
        )

        verify(clusterEntityManager).merge(
            ClusterCertificate(
                "alias",
                CertificateUsage.RPC_API_TLS.publicName,
                "certificate"
            )
        )
    }

    @Test
    fun `importCertificates with invalid node throw an exception`() {
        assertThrows<NoSuchNode> {
            client.importCertificates(
                CertificateUsage.RPC_API_TLS,
                ShortHash.Companion.of("123456789011"),
                "alias",
                "certificate"
            )
        }
    }

    @Test
    fun `retrieveCertificates find the certificate from the RPC API tenant`() {
        client.retrieveCertificates(
            null,
            CertificateUsage.RPC_API_TLS,
            "alias"
        )

        verify(clusterEntityManager).find(
            ClusterCertificate::class.java,
            "alias",
        )
    }

    @Test
    fun `retrieveCertificates find the certificate from the P2P_TLS tenant`() {
        client.retrieveCertificates(
            null,
            CertificateUsage.P2P_TLS,
            "alias"
        )

        verify(clusterEntityManager).find(
            ClusterCertificate::class.java,
            "alias",
        )
    }

    @Test
    fun `retrieveCertificates find the certificate from the node entity`() {
        client.retrieveCertificates(
            nodeTenantId,
            CertificateUsage.P2P_TLS,
            "alias"
        )

        verify(nodeEntityManager).find(
            Certificate::class.java,
            "alias"
        )
    }

    @Test
    fun `retrieveCertificates will close the node factory`() {
        client.retrieveCertificates(
            nodeTenantId,
            CertificateUsage.P2P_TLS,
            "alias"
        )

        verify(nodeFactory).close()
    }

    @Test
    fun `retrieveCertificates will not close the node factory`() {
        client.retrieveCertificates(
            null,
            CertificateUsage.P2P_TLS,
            "alias"
        )

        verify(clusterFactory, never()).close()
    }

    @Test
    fun `importCertificates merge the certificate into the node entity`() {
        client.importCertificates(
            CertificateUsage.RPC_API_TLS,
            nodeTenantId,
            "alias",
            "certificate"
        )

        verify(nodeEntityManager).merge(
            Certificate(
                "alias",
                CertificateUsage.RPC_API_TLS.publicName,
                "certificate",
            )
        )
    }

    @Test
    fun `retrieveCertificates returns null if not found`() {
        val certificate = client.retrieveCertificates(
            nodeTenantId,
            CertificateUsage.P2P_TLS,
            "alias"
        )

        assertThat(certificate).isNull()
    }

    @Test
    fun `retrieveCertificates returns the certificate`() {
        whenever(nodeEntityManager.find(Certificate::class.java, "alias"))
            .doReturn(Certificate("alias", CertificateUsage.P2P_TLS.publicName, "certificate"))

        val certificate = client.retrieveCertificates(
            nodeTenantId,
            CertificateUsage.P2P_TLS,
            "alias"
        )

        assertThat(certificate).isEqualTo("certificate")
    }

    @Test
    fun `retrieveCertificates returns null if the usage is wrong with value`() {
        whenever(nodeEntityManager.find(Certificate::class.java, "alias"))
            .doReturn(Certificate("alias", CertificateUsage.P2P_TLS.publicName, "certificate"))

        val certificate = client.retrieveCertificates(
            nodeTenantId,
            CertificateUsage.P2P_SESSION,
            "alias"
        )

        assertThat(certificate).isNull()
    }

    @Test
    fun `importCertificates will throw an error if there are any`() {
        whenever(clusterEntityManager.merge(any<ClusterCertificate>())).doThrow(RuntimeException("OOPs"))

        assertThrows<RuntimeException> {
            client.importCertificates(
                CertificateUsage.RPC_API_TLS,
                null,
                "alias",
                "certificate"
            )
        }
    }

    @Test
    fun `importCertificates throws exception for unknown tenant ID`() {
        whenever(jpaEntitiesRegistry.get(CordaDb.Vault.persistenceUnitName)) doReturn null

        assertThrows<RuntimeException> {
            client.importCertificates(
                CertificateUsage.RPC_API_TLS,
                nodeTenantId,
                "alias",
                "certificate"
            )
        }
    }
}
