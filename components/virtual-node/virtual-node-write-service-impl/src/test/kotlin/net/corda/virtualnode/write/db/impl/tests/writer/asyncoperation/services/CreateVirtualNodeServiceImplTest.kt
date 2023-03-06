package net.corda.virtualnode.write.db.impl.tests.writer.asyncoperation.services

import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.connection.manager.VirtualNodeDbType.CRYPTO
import net.corda.db.connection.manager.VirtualNodeDbType.UNIQUENESS
import net.corda.db.connection.manager.VirtualNodeDbType.VAULT
import net.corda.db.core.DbPrivilege.DDL
import net.corda.db.core.DbPrivilege.DML
import net.corda.libs.cpi.datamodel.CpkDbChangeLog
import net.corda.libs.cpi.datamodel.CpkDbChangeLogIdentifier
import net.corda.libs.cpi.datamodel.repository.CpkDbChangeLogRepository
import net.corda.libs.virtualnode.common.exception.CpiNotFoundException
import net.corda.libs.virtualnode.common.exception.VirtualNodeAlreadyExistsException
import net.corda.libs.virtualnode.datamodel.repository.HoldingIdentityRepository
import net.corda.libs.virtualnode.datamodel.repository.VirtualNodeRepository
import net.corda.messaging.api.publisher.Publisher
import net.corda.messaging.api.records.Record
import net.corda.virtualnode.write.db.VirtualNodeWriteServiceException
import net.corda.virtualnode.write.db.impl.tests.ALICE_HOLDING_ID1
import net.corda.virtualnode.write.db.impl.tests.CPI_CHECKSUM1
import net.corda.virtualnode.write.db.impl.tests.CPI_IDENTIFIER1
import net.corda.virtualnode.write.db.impl.tests.CPI_METADATA1
import net.corda.virtualnode.write.db.impl.tests.getDbConnection
import net.corda.virtualnode.write.db.impl.tests.getVNodeDb
import net.corda.virtualnode.write.db.impl.tests.getValidRequest
import net.corda.virtualnode.write.db.impl.writer.CpiEntityRepository
import net.corda.virtualnode.write.db.impl.writer.asyncoperation.services.CreateVirtualNodeServiceImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID
import java.util.concurrent.CompletableFuture
import javax.persistence.EntityManager
import javax.persistence.EntityManagerFactory

class CreateVirtualNodeServiceImplTest {
    private val vaultDdlDbConnectionDetails = getDbConnection("vault_ddl", "vault ddl")
    private val vaultDmlDbConnectionDetails = getDbConnection("vault_dml", "vault dml")
    private val cryptoDdlDbConnectionDetails = getDbConnection("crypto_ddl", "crypto ddl")
    private val cryptoDmlDbConnectionDetails = getDbConnection("crypto_dml", "crypto dml")
    private val uniquenessDdlDbConnectionDetails = getDbConnection("uniqueness_ddl", "uniqueness ddl")
    private val uniquenessDmlDbConnectionDetails = getDbConnection("uniqueness_dml", "uniqueness dml")

    private val vaultPlatformManagedVirtualNodeDb = getVNodeDb(
        VAULT,
        true,
        vaultDdlDbConnectionDetails,
        vaultDmlDbConnectionDetails
    )

    private val cryptoUserManagedVirtualNodeDb = getVNodeDb(
        CRYPTO,
        false,
        cryptoDdlDbConnectionDetails,
        cryptoDmlDbConnectionDetails
    )

    private val uniquenessPlatformManagedVirtualNodeDb = getVNodeDb(
        UNIQUENESS,
        true,
        uniquenessDdlDbConnectionDetails,
        uniquenessDmlDbConnectionDetails
    )

    private val virtualNodeDbs = mapOf(
        VAULT to vaultPlatformManagedVirtualNodeDb,
        CRYPTO to cryptoUserManagedVirtualNodeDb,
        UNIQUENESS to uniquenessPlatformManagedVirtualNodeDb,
    )

    private val entityManager = mock<EntityManager>().apply {
        whenever(transaction).thenReturn(mock())
    }
    private val entityManagerFactory = mock<EntityManagerFactory>().apply {
        whenever(createEntityManager()).thenReturn(entityManager)
    }
    private val dbConnectionManager = mock<DbConnectionManager>().apply {
        whenever(getClusterEntityManagerFactory()).thenReturn(entityManagerFactory)
    }
    private val cpkDbChangeLogRepository = mock<CpkDbChangeLogRepository>()
    private val cpiEntityRepository = mock<CpiEntityRepository>()
    private val virtualNodeRepository = mock<VirtualNodeRepository>()
    private val holdingIdentityRepository = mock<HoldingIdentityRepository>()
    private val publisher = mock<Publisher>()

    private val target = CreateVirtualNodeServiceImpl(
        dbConnectionManager,
        cpkDbChangeLogRepository,
        cpiEntityRepository,
        virtualNodeRepository,
        holdingIdentityRepository,
        publisher
    )

    @Test
    fun `publish publishes records to kafka`() {
        val record1 = Record("1", "1", "1")
        val record2 = Record("2", "2", "2")
        val records = listOf(record1, record2)
        val ack1 = CompletableFuture.completedFuture(Unit)
        val ack2 = CompletableFuture.completedFuture(Unit)

        whenever(publisher.publish(any())).thenReturn(listOf(ack1, ack2))

        target.publishRecords(records)

        verify(publisher).publish(records)
    }

    @Test
    fun `run CPI migrations throw on failure`() {
        val cpkChangeLogId1 = CpkDbChangeLogIdentifier("checksum1", "fp1")
        val cpkChangeLogEntity1 = CpkDbChangeLog(cpkChangeLogId1, "content")

        whenever(
            cpkDbChangeLogRepository.findByCpiId(
                entityManager,
                CPI_IDENTIFIER1
            )
        ).thenReturn(listOf(cpkChangeLogEntity1))

        whenever(vaultPlatformManagedVirtualNodeDb.runCpiMigrations(any(), any())).thenThrow(IllegalArgumentException())

        assertThrows<VirtualNodeWriteServiceException> {
            target.runCpiMigrations(CPI_METADATA1, vaultPlatformManagedVirtualNodeDb, ALICE_HOLDING_ID1)
        }
    }

    @Test
    fun `run CPI migrations runs all CPK migrations`() {
        val cpkChangeLogId1 = CpkDbChangeLogIdentifier("checksum1", "fp1")
        val cpkChangeLogId2 = CpkDbChangeLogIdentifier("checksum1", "fp2")
        val cpkChangeLogId3 = CpkDbChangeLogIdentifier("checksum2", "fp1")
        val cpkChangeLogEntity1 = CpkDbChangeLog(cpkChangeLogId1, "content1")
        val cpkChangeLogEntity2 = CpkDbChangeLog(cpkChangeLogId2, "content2")
        val cpkChangeLogEntity3 = CpkDbChangeLog(cpkChangeLogId3, "content3")

        whenever(
            cpkDbChangeLogRepository.findByCpiId(
                entityManager,
                CPI_IDENTIFIER1
            )
        ).thenReturn(listOf(cpkChangeLogEntity1, cpkChangeLogEntity2, cpkChangeLogEntity3))

        target.runCpiMigrations(CPI_METADATA1, vaultPlatformManagedVirtualNodeDb, ALICE_HOLDING_ID1)

        verify(vaultPlatformManagedVirtualNodeDb).runCpiMigrations(any(), eq("checksum1"))
        verify(vaultPlatformManagedVirtualNodeDb).runCpiMigrations(any(), eq("checksum2"))
    }

    @Suppress("LongMethod")
    @Test
    fun `persist virtual node db meta data`() {
        val updateActor = "ua"

        val vaultDdlDbConnectionDetailsId = UUID.randomUUID()
        val vaultDmlDbConnectionDetailsId = UUID.randomUUID()
        val cryptoDdlDbConnectionDetailsId = UUID.randomUUID()
        val cryptoDmlDbConnectionDetailsId = UUID.randomUUID()
        val uniquenessDdlDbConnectionDetailsId = UUID.randomUUID()
        val uniquenessDmlDbConnectionDetailsId = UUID.randomUUID()

        whenever(
            dbConnectionManager.putConnection(
                entityManager,
                vaultDdlDbConnectionDetails.name,
                DDL,
                vaultDdlDbConnectionDetails.config,
                vaultDdlDbConnectionDetails.description,
                updateActor
            )
        ).thenReturn(vaultDdlDbConnectionDetailsId)

        whenever(
            dbConnectionManager.putConnection(
                entityManager,
                vaultDmlDbConnectionDetails.name,
                DML,
                vaultDmlDbConnectionDetails.config,
                vaultDmlDbConnectionDetails.description,
                updateActor
            )
        ).thenReturn(vaultDmlDbConnectionDetailsId)

        whenever(
            dbConnectionManager.putConnection(
                entityManager,
                cryptoDdlDbConnectionDetails.name,
                DDL,
                cryptoDdlDbConnectionDetails.config,
                cryptoDdlDbConnectionDetails.description,
                updateActor
            )
        ).thenReturn(cryptoDdlDbConnectionDetailsId)

        whenever(
            dbConnectionManager.putConnection(
                entityManager,
                cryptoDmlDbConnectionDetails.name,
                DML,
                cryptoDmlDbConnectionDetails.config,
                cryptoDmlDbConnectionDetails.description,
                updateActor
            )
        ).thenReturn(cryptoDmlDbConnectionDetailsId)

        whenever(
            dbConnectionManager.putConnection(
                entityManager,
                uniquenessDdlDbConnectionDetails.name,
                DDL,
                uniquenessDdlDbConnectionDetails.config,
                uniquenessDdlDbConnectionDetails.description,
                updateActor
            )
        ).thenReturn(uniquenessDdlDbConnectionDetailsId)

        whenever(
            dbConnectionManager.putConnection(
                entityManager,
                uniquenessDmlDbConnectionDetails.name,
                DML,
                uniquenessDmlDbConnectionDetails.config,
                uniquenessDmlDbConnectionDetails.description,
                updateActor
            )
        ).thenReturn(uniquenessDmlDbConnectionDetailsId)

        target.persistHoldingIdAndVirtualNode(ALICE_HOLDING_ID1, virtualNodeDbs, CPI_IDENTIFIER1, updateActor)

        verify(holdingIdentityRepository).put(entityManager, ALICE_HOLDING_ID1)

        verify(virtualNodeRepository).put(
            entityManager,
            ALICE_HOLDING_ID1,
            CPI_IDENTIFIER1,
            vaultDdlDbConnectionDetailsId,
            vaultDmlDbConnectionDetailsId,
            cryptoDdlDbConnectionDetailsId,
            cryptoDmlDbConnectionDetailsId,
            uniquenessDdlDbConnectionDetailsId,
            uniquenessDmlDbConnectionDetailsId,
        )
    }

    @Test
    fun `ensure holding identity passes when the new holding id is unique`() {
        val request1 = getValidRequest()
        whenever(virtualNodeRepository.find(eq(entityManager), any())).thenReturn(null)
        whenever(holdingIdentityRepository.find(eq(entityManager), any())).thenReturn(null)

        target.ensureHoldingIdentityIsUnique(request1)
    }

    @Test
    fun `ensure holding identity fails if virtual node already exists`() {
        val request1 = getValidRequest()
        whenever(virtualNodeRepository.find(eq(entityManager), any())).thenReturn(mock())
        whenever(holdingIdentityRepository.find(eq(entityManager), any())).thenReturn(null)

        assertThrows<VirtualNodeAlreadyExistsException> { target.ensureHoldingIdentityIsUnique(request1) }
    }

    @Test
    fun `ensure holding identity fails if virtual short hash already exists`() {
        val request1 = getValidRequest()
        whenever(virtualNodeRepository.find(eq(entityManager), any())).thenReturn(null)
        whenever(holdingIdentityRepository.find(eq(entityManager), any())).thenReturn(mock())

        assertThrows<VirtualNodeWriteServiceException> { target.ensureHoldingIdentityIsUnique(request1) }
    }

    @Test
    fun `get CPI metadata returns data for checksum`() {
        whenever(cpiEntityRepository.getCpiMetadataByChecksum(CPI_CHECKSUM1)).thenReturn(CPI_METADATA1)

        assertThat(target.getCpiMetaData(CPI_CHECKSUM1)).isEqualTo(CPI_METADATA1)
    }

    @Test
    fun `get CPI metadata throws if metadata not found`() {
        whenever(cpiEntityRepository.getCpiMetadataByChecksum(CPI_CHECKSUM1)).thenReturn(null)

        assertThrows<CpiNotFoundException> { target.getCpiMetaData(CPI_CHECKSUM1) }
    }

    @Test
    fun `validate request returns null for valid request`() {
        val request1 = getValidRequest()
        assertThat(target.validateRequest(request1)).isNull()
    }

    @Test
    fun `validate request ensures cpi checksum is specified`() {
        val request1 = getValidRequest().apply { cpiFileChecksum = "" }
        val request2 = getValidRequest().apply { cpiFileChecksum = null }

        assertThat(target.validateRequest(request1)).isEqualTo("CPI file checksum value is missing")
        assertThat(target.validateRequest(request2)).isEqualTo("CPI file checksum value is missing")
    }
}