package net.corda.membership.certificate.service.impl

import net.corda.crypto.core.CryptoTenants
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DbUtils
import net.corda.membership.certificate.service.CertificatesService
import net.corda.membership.certificates.datamodel.Certificate
import net.corda.membership.certificates.datamodel.CertificateEntities
import net.corda.membership.certificates.datamodel.ClusterCertificate
import net.corda.membership.certificates.datamodel.ClusterCertificatePrimaryKey
import net.corda.orm.JpaEntitiesRegistry
import net.corda.orm.impl.EntityManagerFactoryFactoryImpl
import net.corda.orm.utils.transaction
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import javax.persistence.EntityManagerFactory

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class CertificatesServiceImplTest {
    private val emConfig = DbUtils.getEntityManagerConfiguration("db_for_test")
    private val entityManagerFactory: EntityManagerFactory
    private val certificatesService: CertificatesService

    companion object {
        private const val CLUSTER_CERTIFICATE_MIGRATION_FILE = "net/corda/db/schema/cluster-certificates/db.changelog-master.xml"
        private const val VNODE_CERTIFICATE_MIGRATION_FILE = "net/corda/db/schema/vnode-vault/db.changelog-master.xml"
    }

    /**
     * Creates an in-memory database, applies the relevant migration scripts
     */
    init {
        val dbChange = ClassloaderChangeLog(
            linkedSetOf(
                ClassloaderChangeLog.ChangeLogResourceFiles(
                    DbSchema::class.java.packageName,
                    listOf(CLUSTER_CERTIFICATE_MIGRATION_FILE, VNODE_CERTIFICATE_MIGRATION_FILE),
                    DbSchema::class.java.classLoader
                )
            )
        )
        emConfig.dataSource.connection.use { connection ->
            LiquibaseSchemaMigratorImpl().updateDb(connection, dbChange)
        }
        entityManagerFactory = EntityManagerFactoryFactoryImpl().create(
            "test_cluster_unit",
            CertificateEntities.clusterClasses.toList() + CertificateEntities.vnodeClasses.toList(),
            emConfig
        )
        val jpaEntitiesRegistryMock = mock<JpaEntitiesRegistry>().apply {
            whenever(get(any())) doReturn mock()
        }
        val virtualNodeInfoMock = mock<VirtualNodeInfo>().apply {
            whenever(vaultDmlConnectionId) doReturn mock()
        }
        val virtualNodeInfoReadServiceMock = mock<VirtualNodeInfoReadService>().apply {
            whenever(getByHoldingIdentityShortHash(any())) doReturn virtualNodeInfoMock
        }
        val dbConnectionManagerMock = mock<DbConnectionManager>().apply {
            whenever(getClusterEntityManagerFactory()) doReturn entityManagerFactory
            whenever(createEntityManagerFactory(any(), any())) doAnswer {
                EntityManagerFactoryFactoryImpl().create(
                    "test_vnode_unit",
                    CertificateEntities.vnodeClasses.toList(),
                    DbUtils.getEntityManagerConfiguration("db_for_test")
                )
            }
        }
        certificatesService = CertificatesServiceImpl(
            mock(),
            mock(),
            dbConnectionManagerMock,
            jpaEntitiesRegistryMock,
            mock(),
            virtualNodeInfoReadServiceMock)
    }

    @Suppress("Unused")
    @AfterAll
    fun cleanup() {
        emConfig.close()
        entityManagerFactory.close()
    }

    @Test
    fun `imports cluster certificate`() {

        val testTenant = CryptoTenants.CODE_SIGNER
        val testAlias = "testAlias"
        val testRawCertificate = "testRawCertificate"
        entityManagerFactory.transaction {
            it.createQuery("delete from ClusterCertificate").executeUpdate()
        }

        certificatesService.importCertificates(testTenant, testAlias, testRawCertificate)

        val importedCertificate = entityManagerFactory.transaction {
            it.find(ClusterCertificate::class.java, ClusterCertificatePrimaryKey(testTenant, testAlias))
        }
        assertThat(importedCertificate).isEqualTo(ClusterCertificate(testTenant, testAlias, testRawCertificate))
    }

    @Test
    fun `returns null when cluster certificate not found by alias`() {

        val testTenant = CryptoTenants.P2P
        val testAlias = "testAlias"
        entityManagerFactory.transaction {
            it.createQuery("delete from ClusterCertificate").executeUpdate()
            it.persist(ClusterCertificate("otherTenant", testAlias, "otherCertificate"))
        }

        val certificate = certificatesService.retrieveCertificates(testTenant, testAlias)

        assertThat(certificate).isNull()
    }

    @Test
    fun `retrieves cluster certificate by alias`() {

        val testTenant = CryptoTenants.CODE_SIGNER
        val testAlias = "testAlias"
        val testRawCertificate = "testRawCertificate"
        entityManagerFactory.transaction {
            it.createQuery("delete from ClusterCertificate").executeUpdate()
            it.persist(ClusterCertificate(testTenant, testAlias, testRawCertificate))
            it.persist(ClusterCertificate("otherTenant", testAlias, "otherCertificate"))
        }

        val certificate = certificatesService.retrieveCertificates(testTenant, testAlias)

        assertThat(certificate).isEqualTo(testRawCertificate)
    }

    @Test
    fun `returns empty list when tenant's cluster certificates not found`() {

        val testTenant = CryptoTenants.P2P
        entityManagerFactory.transaction {
            it.createQuery("delete from ClusterCertificate").executeUpdate()
            it.persist(ClusterCertificate("otherTenant", "otherAlias", "otherCertificate"))
        }

        val certificate = certificatesService.retrieveAllCertificates(testTenant)

        assertThat(certificate).isEmpty()
    }

    @Test
    fun `retrieves all tenant's cluster certificates`() {

        val testTenant = CryptoTenants.RPC_API
        val testRawCertificate1 = "testRawCertificate1"
        val testRawCertificate2 = "testRawCertificate2"
        entityManagerFactory.transaction {
            it.createQuery("delete from ClusterCertificate").executeUpdate()
            it.persist(ClusterCertificate(testTenant, "testAlias1", testRawCertificate1))
            it.persist(ClusterCertificate(testTenant, "testAlias2", testRawCertificate2))
            it.persist(ClusterCertificate("otherTenant", "otherAlias", "otherCertificate"))
        }

        val certificates = certificatesService.retrieveAllCertificates(testTenant)

        assertThat(certificates.size).isEqualTo(2)
        assertThat(certificates.toSet()).isEqualTo(setOf(testRawCertificate1, testRawCertificate2))
    }

    @Test
    fun `imports virtual node certificate`() {

        val testTenant = "012345678901"
        val testAlias = "testAlias"
        val testRawCertificate = "testRawCertificate"
        entityManagerFactory.transaction {
            it.createQuery("delete from Certificate").executeUpdate()
        }

        certificatesService.importCertificates(testTenant, testAlias, testRawCertificate)

        val importedCertificate = entityManagerFactory.transaction {
            it.find(Certificate::class.java, testAlias)
        }
        assertThat(importedCertificate).isEqualTo(Certificate(testAlias, testRawCertificate))
    }

    @Test
    fun `returns null when virtual node certificate not found by alias`() {
        val testTenant = "012345678901"
        val testAlias = "testAlias"
        entityManagerFactory.transaction {
            it.createQuery("delete from Certificate").executeUpdate()
            it.persist(Certificate("otherAlias", "otherCertificate"))
        }

        val certificate = certificatesService.retrieveCertificates(testTenant, testAlias)

        assertThat(certificate).isNull()
    }

    @Test
    fun `retrieves virtual node certificate by alias`() {
        val testTenant = "012345678902"
        val testAlias = "testAlias"
        val testRawCertificate = "testRawCertificate"
        entityManagerFactory.transaction {
            it.createQuery("delete from Certificate").executeUpdate()
            it.persist(Certificate(testAlias, testRawCertificate))
            it.persist(Certificate("otherAlias", "otherCertificate"))
        }

        val certificate = certificatesService.retrieveCertificates(testTenant, testAlias)

        assertThat(certificate).isEqualTo(testRawCertificate)
    }

    @Test
    fun `returns empty list when virtual node certificates not found`() {
        val testTenant = "012345678903"
        entityManagerFactory.transaction {
            it.createQuery("delete from Certificate").executeUpdate()
        }

        val certificate = certificatesService.retrieveAllCertificates(testTenant)

        assertThat(certificate).isEmpty()
    }

    @Test
    fun `retrieves all virtual node's certificates`() {
        val testTenant = "012345678904"
        val testRawCertificate1 = "testRawCertificate1"
        val testRawCertificate2 = "testRawCertificate2"
        entityManagerFactory.transaction {
            it.createQuery("delete from Certificate").executeUpdate()
            it.persist(Certificate("testAlias1", testRawCertificate1))
            it.persist(Certificate("testAlias2", testRawCertificate2))
        }

        val certificates = certificatesService.retrieveAllCertificates(testTenant)

        assertThat(certificates.size).isEqualTo(2)
        assertThat(certificates.toSet()).isEqualTo(setOf(testRawCertificate1, testRawCertificate2))
    }
}
