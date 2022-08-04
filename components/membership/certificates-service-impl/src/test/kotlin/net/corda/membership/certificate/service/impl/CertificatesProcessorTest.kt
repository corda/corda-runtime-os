package net.corda.membership.certificate.service.impl

import net.corda.crypto.core.CryptoTenants
import net.corda.data.certificates.rpc.request.CertificateRpcRequest
import net.corda.data.certificates.rpc.request.ImportCertificateRpcRequest
import net.corda.data.certificates.rpc.request.RetrieveCertificateRpcRequest
import net.corda.data.certificates.rpc.response.CertificateImportedRpcResponse
import net.corda.data.certificates.rpc.response.CertificateRetrievalRpcResponse
import net.corda.data.certificates.rpc.response.CertificateRpcResponse
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.core.DbPrivilege
import net.corda.db.schema.CordaDb
import net.corda.membership.certificates.datamodel.Certificate
import net.corda.membership.certificates.datamodel.ClusterCertificate
import net.corda.membership.certificates.datamodel.ClusterCertificatePrimaryKey
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.JpaEntitiesSet
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
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
    private val nodeTenantId = "ID1"
    private val dbConnectionManager = mock<DbConnectionManager> {
        on { getClusterEntityManagerFactory() } doReturn clusterFactory
        on { getOrCreateEntityManagerFactory(eq("vnode_vault_id1"), eq(DbPrivilege.DML), eq(registry)) } doReturn nodeFactory
    }
    private val nodeInfo = mock<VirtualNodeInfo>()
    private val virtualNodeInfoReadService = mock<VirtualNodeInfoReadService> {
        on { getByHoldingIdentityShortHash(nodeTenantId) } doReturn nodeInfo
    }
    private val response = CompletableFuture<CertificateRpcResponse>()

    private val processor = CertificatesProcessor(dbConnectionManager, jpaEntitiesRegistry, virtualNodeInfoReadService)

    @Test
    fun `onNext merge the certificate into the p2p tenant`() {
        val request = CertificateRpcRequest(
            CryptoTenants.P2P,
            ImportCertificateRpcRequest("alias", "certificate")
        )

        processor.onNext(request, response)

        verify(clusterEntityManager).merge(ClusterCertificate(CryptoTenants.P2P, "alias", "certificate"))
    }

    @Test
    fun `onNext merge the certificate into the RPC API tenant`() {
        val request = CertificateRpcRequest(
            CryptoTenants.RPC_API,
            ImportCertificateRpcRequest("alias", "certificate")
        )

        processor.onNext(request, response)

        verify(clusterEntityManager).merge(ClusterCertificate(CryptoTenants.RPC_API, "alias", "certificate"))
    }

    @Test
    fun `onNext find the certificate from the RPC API tenant`() {
        val request = CertificateRpcRequest(
            CryptoTenants.RPC_API,
            RetrieveCertificateRpcRequest("alias")
        )

        processor.onNext(request, response)

        verify(clusterEntityManager).find(
            ClusterCertificate::class.java,
            ClusterCertificatePrimaryKey(CryptoTenants.RPC_API, "alias")
        )
    }

    @Test
    fun `onNext find the certificate from the P2P tenant`() {
        val request = CertificateRpcRequest(
            CryptoTenants.P2P,
            RetrieveCertificateRpcRequest("alias")
        )

        processor.onNext(request, response)

        verify(clusterEntityManager).find(
            ClusterCertificate::class.java,
            ClusterCertificatePrimaryKey(CryptoTenants.P2P, "alias")
        )
    }

    @Test
    fun `onNext find the certificate from the node entity`() {
        val request = CertificateRpcRequest(
            nodeTenantId,
            RetrieveCertificateRpcRequest("alias")
        )

        processor.onNext(request, response)

        verify(nodeEntityManager).find(
            Certificate::class.java,
            "alias"
        )
    }

    @Test
    fun `onNext merge the certificate into the node entity`() {
        val request = CertificateRpcRequest(
            nodeTenantId,
            ImportCertificateRpcRequest("alias", "certificate")
        )

        processor.onNext(request, response)

        verify(nodeEntityManager).merge(Certificate("alias", "certificate"))
    }

    @Test
    fun `onNext returns CertificateRetrievalRpcResponse without value`() {
        val request = CertificateRpcRequest(
            nodeTenantId,
            RetrieveCertificateRpcRequest("alias")
        )

        processor.onNext(request, response)

        assertThat(response).isCompletedWithValue(CertificateRpcResponse(CertificateRetrievalRpcResponse(null)))
    }

    @Test
    fun `onNext returns CertificateRetrievalRpcResponse with value`() {
        whenever(nodeEntityManager.find(Certificate::class.java, "alias"))
            .doReturn(Certificate("alias", "certificate"))
        val request = CertificateRpcRequest(
            nodeTenantId,
            RetrieveCertificateRpcRequest("alias")
        )

        processor.onNext(request, response)

        assertThat(response).isCompletedWithValue(CertificateRpcResponse(CertificateRetrievalRpcResponse("certificate")))
    }

    @Test
    fun `onNext will return CertificateImportedRpcResponse`() {
        val request = CertificateRpcRequest(
            CryptoTenants.RPC_API,
            ImportCertificateRpcRequest("alias", "certificate")
        )

        processor.onNext(request, response)

        assertThat(response).isCompletedWithValue(CertificateRpcResponse(CertificateImportedRpcResponse()))
    }

    @Test
    fun `onNext will throw an error if there are any`() {
        whenever(clusterEntityManager.merge(any<ClusterCertificate>())).doThrow(RuntimeException("OOPs"))
        val request = CertificateRpcRequest(
            CryptoTenants.RPC_API,
            ImportCertificateRpcRequest("alias", "certificate")
        )

        processor.onNext(request, response)

        assertThat(response).isCompletedExceptionally
    }

    @Test
    fun `onNext will throw an error for unexpected request`() {
        val request = CertificateRpcRequest(
            CryptoTenants.RPC_API,
            null
        )

        processor.onNext(request, response)

        assertThat(response).isCompletedExceptionally
    }

    @Test
    fun `onNext throws exception for unknown node`() {
        val request = CertificateRpcRequest(
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
            nodeTenantId,
            ImportCertificateRpcRequest("alias", "certificate")
        )

        processor.onNext(request, response)

        assertThat(response).isCompletedExceptionally
    }
}
