package net.corda.membership.impl.persistence.service.handler

import net.corda.data.identity.HoldingIdentity
import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.MutualTlsAddToAllowedCertificates
import net.corda.data.p2p.AllowedCertificateSubject
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.CordaDb
import net.corda.membership.datamodel.MutualTlsAllowedClientCertificateEntity
import net.corda.membership.mtls.allowed.list.service.AllowedCertificatesReaderWriterService
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.JpaEntitiesSet
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import net.corda.virtualnode.toCorda
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.util.UUID
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory
import javax.persistence.EntityTransaction

class MutualTlsAddToAllowedCertificatesHandlerTest {
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
    private val handler = MutualTlsAddToAllowedCertificatesHandler(persistenceHandlerServices)
    private val context = mock<MembershipRequestContext> {
        on { holdingIdentity } doReturn holdingIdentity
    }
    private val request = MutualTlsAddToAllowedCertificates(
        "subject"
    )

    @Test
    fun `invoke merge the subject`() {
        handler.invoke(
            context,
            request,
        )

        verify(entityManager).merge(
            MutualTlsAllowedClientCertificateEntity("subject", false)
        )
    }

    @Test
    fun `invoke will publish the subject`() {
        handler.invoke(
            context,
            request,
        )

        verify(writerToKafka).put(
            AllowedCertificateSubject(request.subject),
            AllowedCertificateSubject(request.subject),
        )
    }
}