package net.corda.membership.certificate.service.impl

import net.corda.data.certificates.CertificateUsage
import net.corda.data.certificates.rpc.request.CertificateRpcRequest
import net.corda.data.certificates.rpc.request.ImportCertificateRpcRequest
import net.corda.data.certificates.rpc.request.RetrieveCertificateRpcRequest
import net.corda.data.certificates.rpc.response.CertificateImportedRpcResponse
import net.corda.data.certificates.rpc.response.CertificateRetrievalRpcResponse
import net.corda.data.certificates.rpc.response.CertificateRpcResponse
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.membership.certificates.CertificateUsageUtils.publicName
import net.corda.membership.certificates.datamodel.Certificate
import net.corda.membership.certificates.datamodel.ClusterCertificate
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.JpaEntitiesSet
import net.corda.virtualnode.ShortHash
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID
import java.util.concurrent.CompletableFuture
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityTransaction

class CertificatesProcessorTest {
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
    private val response = CompletableFuture<CertificateRpcResponse>()

    private val processor = CertificatesProcessor(dbConnectionManager, jpaEntitiesRegistry, virtualNodeInfoReadService)

    @Test
    fun `onNext merge the certificate into the P2P_TLS tenant`() {
        val request = CertificateRpcRequest(
            CertificateUsage.P2P_TLS,
            null,
            ImportCertificateRpcRequest("alias", "certificate")
        )

        processor.onNext(request, response)

        verify(clusterEntityManager).merge(
            ClusterCertificate("alias", CertificateUsage.P2P_TLS.publicName, "certificate")
        )
    }

    @Test
    fun `onNext merge the certificate into the RPC API tenant`() {
        val request = CertificateRpcRequest(
            CertificateUsage.RPC_API_TLS,
            null,
            ImportCertificateRpcRequest("alias", "certificate")
        )

        processor.onNext(request, response)

        verify(clusterEntityManager).merge(ClusterCertificate(
            "alias", CertificateUsage.RPC_API_TLS.publicName, "certificate")
        )
    }

    @Test
    fun `onNext find the certificate from the RPC API tenant`() {
        val request = CertificateRpcRequest(
            CertificateUsage.RPC_API_TLS,
            null,
            RetrieveCertificateRpcRequest("alias")
        )

        processor.onNext(request, response)

        verify(clusterEntityManager).find(
            ClusterCertificate::class.java,
            "alias",
        )
    }

    @Test
    fun `onNext find the certificate from the P2P_TLS tenant`() {
        val request = CertificateRpcRequest(
            CertificateUsage.P2P_TLS,
            null,
            RetrieveCertificateRpcRequest("alias")
        )

        processor.onNext(request, response)

        verify(clusterEntityManager).find(
            ClusterCertificate::class.java,
            "alias",
        )
    }

    @Test
    fun `onNext find the certificate from the node entity`() {
        val request = CertificateRpcRequest(
            CertificateUsage.P2P_SESSION,
            nodeTenantId.value,
            RetrieveCertificateRpcRequest("alias")
        )

        processor.onNext(request, response)

        verify(nodeEntityManager).find(
            Certificate::class.java,
            "alias"
        )
    }

    @Test
    fun `onNext will close the node factory`() {
        val request = CertificateRpcRequest(
            CertificateUsage.P2P_SESSION,
            nodeTenantId.value,
            RetrieveCertificateRpcRequest("alias")
        )

        processor.onNext(request, response)

        verify(nodeFactory).close()
    }

    @Test
    fun `onNext will not close the node factory`() {
        val request = CertificateRpcRequest(
            CertificateUsage.P2P_TLS,
            null,
            RetrieveCertificateRpcRequest("alias")
        )

        processor.onNext(request, response)

        verify(clusterFactory, never()).close()
    }

    @Test
    fun `onNext merge the certificate into the node entity`() {
        val request = CertificateRpcRequest(
            CertificateUsage.RPC_API_TLS,
            nodeTenantId.value,
            ImportCertificateRpcRequest("alias", "certificate")
        )

        processor.onNext(request, response)

        verify(nodeEntityManager).merge(
            Certificate(
                "alias",
                CertificateUsage.RPC_API_TLS.publicName,
                "certificate",
            )
        )
    }

    @Test
    fun `onNext returns CertificateRetrievalRpcResponse without value`() {
        val request = CertificateRpcRequest(
            CertificateUsage.P2P_TLS,
            nodeTenantId.value,
            RetrieveCertificateRpcRequest("alias")
        )

        processor.onNext(request, response)

        assertThat(response).isCompletedWithValue(CertificateRpcResponse(CertificateRetrievalRpcResponse(null)))
    }

    @Test
    fun `onNext returns CertificateRetrievalRpcResponse with value`() {
        whenever(nodeEntityManager.find(Certificate::class.java, "alias"))
            .doReturn(Certificate("alias", CertificateUsage.P2P_TLS.publicName, "certificate"))
        val request = CertificateRpcRequest(
            CertificateUsage.P2P_TLS,
            nodeTenantId.value,
            RetrieveCertificateRpcRequest("alias")
        )

        processor.onNext(request, response)

        assertThat(response).isCompletedWithValue(CertificateRpcResponse(CertificateRetrievalRpcResponse("certificate")))
    }

    @Test
    fun `onNext will return CertificateImportedRpcResponse`() {
        val request = CertificateRpcRequest(
            CertificateUsage.RPC_API_TLS,
            null,
            ImportCertificateRpcRequest("alias", "certificate")
        )

        processor.onNext(request, response)

        assertThat(response).isCompletedWithValue(CertificateRpcResponse(CertificateImportedRpcResponse()))
    }

    @Test
    fun `onNext will throw an error if there are any`() {
        whenever(clusterEntityManager.merge(any<ClusterCertificate>())).doThrow(RuntimeException("OOPs"))
        val request = CertificateRpcRequest(
            CertificateUsage.RPC_API_TLS,
            null,
            ImportCertificateRpcRequest("alias", "certificate")
        )

        processor.onNext(request, response)

        assertThat(response).isCompletedExceptionally
    }

    @Test
    fun `onNext will throw an error for unexpected request`() {
        val request = CertificateRpcRequest(
            CertificateUsage.RPC_API_TLS,
            null,
            null,
        )

        processor.onNext(request, response)

        assertThat(response).isCompletedExceptionally
    }

    @Test
    fun `onNext throws exception for unknown node`() {
        val request = CertificateRpcRequest(
            CertificateUsage.RPC_API_TLS,
            "Nop",
            ImportCertificateRpcRequest("alias", "certificate")
        )

        processor.onNext(request, response)

        assertThat(response).isCompletedExceptionally
    }

    @Test
    fun `onNext throws exception for unknown tenant ID`() {
        whenever(jpaEntitiesRegistry.get(CordaDb.Vault.persistenceUnitName)) doReturn null
        val request = CertificateRpcRequest(
            CertificateUsage.RPC_API_TLS,
            nodeTenantId.value,
            ImportCertificateRpcRequest("alias", "certificate")
        )

        processor.onNext(request, response)

        assertThat(response).isCompletedExceptionally
    }
}
