package net.corda.membership.certificate.service.impl

import net.corda.crypto.core.ShortHash
import net.corda.data.certificates.CertificateUsage
import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.schema.DbSchema
import net.corda.db.testkit.DbUtils
import net.corda.membership.certificate.service.CertificatesService
import net.corda.membership.certificates.CertificateUsageUtils.publicName
import net.corda.membership.certificates.datamodel.Certificate
import net.corda.membership.certificates.datamodel.CertificateEntities
import net.corda.membership.certificates.datamodel.ClusterCertificate
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
        private const val CONFIG_CERTIFICATE_MIGRATION_FILE = "net/corda/db/schema/config/db.changelog-master.xml"
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
                    listOf(CONFIG_CERTIFICATE_MIGRATION_FILE, VNODE_CERTIFICATE_MIGRATION_FILE),
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

        val usage = CertificateUsage.CODE_SIGNER
        val testAlias = "testAlias"
        val testRawCertificate = "testRawCertificate"
        entityManagerFactory.transaction {
            it.createQuery("delete from ClusterCertificate").executeUpdate()
        }

        certificatesService.client.importCertificates(usage, null, testAlias, testRawCertificate)

        val importedCertificate = entityManagerFactory.transaction {
            it.find(ClusterCertificate::class.java, testAlias)
        }
        assertThat(importedCertificate).isEqualTo(ClusterCertificate(testAlias, CertificateUsage.CODE_SIGNER.publicName, testRawCertificate))
    }

    @Test
    fun `returns null when cluster certificate not found by alias`() {

        val testUsage = CertificateUsage.P2P_TLS
        val testAlias = "testAlias"
        entityManagerFactory.transaction {
            it.createQuery("delete from ClusterCertificate").executeUpdate()
            it.persist(ClusterCertificate("otherTenant", testAlias, "otherCertificate"))
        }

        val certificate = certificatesService.client.retrieveCertificates( null, testUsage, testAlias)

        assertThat(certificate).isNull()
    }

    @Test
    fun `retrieves cluster certificate by alias`() {

        val testUsage = CertificateUsage.CODE_SIGNER
        val testAlias = "testAlias"
        val testRawCertificate = "testRawCertificate"
        entityManagerFactory.transaction {
            it.createQuery("delete from ClusterCertificate").executeUpdate()
            it.persist(
                ClusterCertificate(
                    usage = testUsage.publicName,
                    alias = testAlias,
                    rawCertificate = testRawCertificate
                )
            )
            it.persist(
                ClusterCertificate(
                    usage = "otherTenant",
                    alias = "OtherAlias",
                    rawCertificate = "otherCertificate"
                )
            )
        }

        val certificate = certificatesService.client.retrieveCertificates(null, testUsage, testAlias)

        assertThat(certificate).isEqualTo(testRawCertificate)
    }

    @Test
    fun `returns empty list when tenant's cluster certificates not found`() {

        val testUsage = CertificateUsage.P2P_TLS
        entityManagerFactory.transaction {
            it.createQuery("delete from ClusterCertificate").executeUpdate()
            it.persist(ClusterCertificate("otherTenant", "otherAlias", "otherCertificate"))
        }

        val aliases = certificatesService.client.getCertificateAliases(testUsage, null)

        assertThat(aliases).isEmpty()
    }

    @Test
    fun `retrieves all tenant's cluster certificates`() {

        val testUsage = CertificateUsage.REST_TLS
        val testRawCertificate1 = "testRawCertificate1"
        val testRawCertificate2 = "testRawCertificate2"
        entityManagerFactory.transaction {
            it.createQuery("delete from ClusterCertificate").executeUpdate()
            it.persist(ClusterCertificate("testAlias1", testUsage.publicName, testRawCertificate1))
            it.persist(ClusterCertificate("testAlias2", testUsage.publicName, testRawCertificate2))
            it.persist(ClusterCertificate("otherAlias", "OtherUsage", "otherCertificate"))
        }

        val certificates = certificatesService.client.getCertificateAliases(testUsage, null)

        assertThat(certificates).containsExactlyInAnyOrder(
            "testAlias1",
            "testAlias2"
        )
    }

    @Test
    fun `imports virtual node certificate`() {

        val testTenant = ShortHash.of("012345678901")
        val testAlias = "testAlias"
        val testRawCertificate = "testRawCertificate"
        entityManagerFactory.transaction {
            it.createQuery("delete from Certificate").executeUpdate()
        }

        certificatesService.client.importCertificates(CertificateUsage.REST_TLS, testTenant, testAlias, testRawCertificate)

        val importedCertificate = entityManagerFactory.transaction {
            it.find(Certificate::class.java, testAlias)
        }
        assertThat(importedCertificate).isEqualTo(Certificate(testAlias, CertificateUsage.REST_TLS.publicName, testRawCertificate))
    }

    @Test
    fun `returns null when virtual node certificate not found by alias`() {
        val testTenant = ShortHash.of("012345678901")
        val testAlias = "testAlias"
        entityManagerFactory.transaction {
            it.createQuery("delete from Certificate").executeUpdate()
            it.persist(Certificate("otherAlias", CertificateUsage.P2P_TLS.publicName, "otherCertificate"))
        }

        val certificate = certificatesService.client.retrieveCertificates( testTenant, CertificateUsage.P2P_TLS, testAlias)

        assertThat(certificate).isNull()
    }

    @Test
    fun `retrieves virtual node certificate by alias`() {
        val testTenant = ShortHash.of("012345678902")
        val testAlias = "testAlias"
        val testRawCertificate = "testRawCertificate"
        entityManagerFactory.transaction {
            it.createQuery("delete from Certificate").executeUpdate()
            it.persist(Certificate(testAlias, CertificateUsage.REST_TLS.publicName, testRawCertificate))
            it.persist(Certificate("otherAlias", CertificateUsage.REST_TLS.publicName, "otherCertificate"))
        }

        val certificate = certificatesService.client.retrieveCertificates(testTenant, CertificateUsage.REST_TLS, testAlias)

        assertThat(certificate).isEqualTo(testRawCertificate)
    }

    @Test
    fun `returns empty list when virtual node certificates not found`() {
        val testTenant = ShortHash.of("012345678903")
        entityManagerFactory.transaction {
            it.createQuery("delete from Certificate").executeUpdate()
        }

        val aliases = certificatesService.client.getCertificateAliases(CertificateUsage.P2P_SESSION, testTenant)

        assertThat(aliases).isEmpty()
    }

    @Test
    fun `retrieves all virtual node's certificates`() {
        val testTenant = ShortHash.of("012345678904")
        val testRawCertificate1 = "testRawCertificate1"
        val testRawCertificate2 = "testRawCertificate2"
        entityManagerFactory.transaction {
            it.createQuery("delete from Certificate").executeUpdate()
            it.persist(Certificate("testAlias1", CertificateUsage.P2P_SESSION.publicName, testRawCertificate1))
            it.persist(Certificate("testAlias2", CertificateUsage.P2P_SESSION.publicName, testRawCertificate2))
        }

        val aliases = certificatesService.client.getCertificateAliases(CertificateUsage.P2P_SESSION, testTenant)

        assertThat(aliases).containsExactlyInAnyOrder(
            "testAlias1",
            "testAlias2",
        )
    }
}
