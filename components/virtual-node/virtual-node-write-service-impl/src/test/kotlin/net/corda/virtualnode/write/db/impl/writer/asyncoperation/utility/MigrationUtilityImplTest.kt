package net.corda.virtualnode.write.db.impl.writer.asyncoperation.utility

import net.corda.crypto.core.ShortHash
import java.lang.IllegalArgumentException
import java.sql.Connection
import java.util.UUID
import javax.persistence.PersistenceException
import net.corda.db.admin.LiquibaseSchemaMigrator
import net.corda.db.connection.manager.DbConnectionManager
import net.corda.db.core.CloseableDataSource
import net.corda.libs.cpi.datamodel.CpkDbChangeLogIdentifier
import net.corda.libs.cpi.datamodel.CpkDbChangeLog
import net.corda.virtualnode.write.db.VirtualNodeWriteServiceException
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeDbChangeLog
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class MigrationUtilityImplTest {

    private val connection = mock<Connection>()
    private val datasource = mock<CloseableDataSource>() {
        whenever(it.connection).thenReturn(connection)
    }
    private val dbConnectionManager = mock<DbConnectionManager>() {
        whenever(it.createDatasource(any())).thenReturn(datasource)
    }
    private val liquibaseSchemaMigrator = mock<LiquibaseSchemaMigrator>()
    private val migrationUtility = MigrationUtilityImpl(dbConnectionManager, liquibaseSchemaMigrator)
    private val vaultDdlConnectionId = UUID.randomUUID()
    private val cpk1DogsChangelog = mock<CpkDbChangeLog> {
        whenever(it.id).thenReturn(CpkDbChangeLogIdentifier("cpk1","dogs.xml"))
        whenever(it.content).thenReturn("content-dogs")
    }
    private val cpk1CatsChangelog = mock<CpkDbChangeLog> {
        whenever(it.id).thenReturn(CpkDbChangeLogIdentifier("cpk1","cats.xml"))
        whenever(it.content).thenReturn("content-cats")
    }
    private val cpk2RabbitsChangelog = mock<CpkDbChangeLog> {
        whenever(it.id).thenReturn(CpkDbChangeLogIdentifier("cpk2","rabbits.xml"))
        whenever(it.content).thenReturn("content-rabbits")
    }
    private val cpk2SnakesChangelog = mock<CpkDbChangeLog> {
        whenever(it.id).thenReturn(CpkDbChangeLogIdentifier("cpk2","snakes.xml"))
        whenever(it.content).thenReturn("content-snakes")
    }

    @Test
    fun `runVaultMigrations with no changelogs does nothing`() {
        migrationUtility.runVaultMigrations(
            ShortHash.Companion.of("123456789011"),
            emptyList(),
            vaultDdlConnectionId
        )

        verify(dbConnectionManager, times(0)).createDatasource(any())
        verify(liquibaseSchemaMigrator, times(0)).updateDb(any(), any(), tag = any())
    }

    @Test
    fun `runVaultMigrations with changelogs in one CPK runs them with schema migrator`() {
        val changelogsCapture = argumentCaptor<VirtualNodeDbChangeLog>()

        migrationUtility.runVaultMigrations(
            ShortHash.Companion.of("123456789011"),
            listOf(cpk1DogsChangelog, cpk1CatsChangelog),
            vaultDdlConnectionId
        )

        verify(liquibaseSchemaMigrator).updateDb(eq(connection), changelogsCapture.capture(), tag = eq("cpk1"))

        assertThat(changelogsCapture.firstValue).isNotNull
        val changelogs = changelogsCapture.firstValue
        assertThat(changelogs.changeLogFileList).isEqualTo(setOf("dogs.xml", "cats.xml"))
    }

    @Test
    fun `runVaultMigrations with changelogs in multiple CPKs runs them with schema migrator`() {
        val changelogsCapture = argumentCaptor<VirtualNodeDbChangeLog>()

        migrationUtility.runVaultMigrations(
            ShortHash.Companion.of("123456789011"),
            listOf(cpk1DogsChangelog, cpk1CatsChangelog, cpk2RabbitsChangelog, cpk2SnakesChangelog),
            vaultDdlConnectionId
        )

        verify(liquibaseSchemaMigrator).updateDb(eq(connection), changelogsCapture.capture(), tag = eq("cpk1"))
        verify(liquibaseSchemaMigrator).updateDb(eq(connection), changelogsCapture.capture(), tag = eq("cpk2"))

        assertThat(changelogsCapture.firstValue).isNotNull
        val cpk1Changelogs = changelogsCapture.firstValue
        assertThat(cpk1Changelogs.changeLogFileList).isEqualTo(setOf("dogs.xml", "cats.xml"))
        assertThat(changelogsCapture.secondValue).isNotNull
        val cpk2Changelogs = changelogsCapture.secondValue
        assertThat(cpk2Changelogs.changeLogFileList).isEqualTo(setOf("rabbits.xml", "snakes.xml"))
    }

    @Test
    fun `exception running migrations throws VirtualNodeWriteServiceException`() {
        val changelogsCapture = argumentCaptor<VirtualNodeDbChangeLog>()

        whenever(liquibaseSchemaMigrator.updateDb(eq(connection), changelogsCapture.capture(), tag = eq("cpk1")))
            .thenThrow(PersistenceException("error running migrations"))

        assertThrows<VirtualNodeWriteServiceException> {
            migrationUtility.runVaultMigrations(
                ShortHash.Companion.of("123456789011"),
                listOf(cpk1DogsChangelog, cpk1CatsChangelog, cpk2RabbitsChangelog, cpk2SnakesChangelog),
                vaultDdlConnectionId
            )
        }

        verify(liquibaseSchemaMigrator, times(0)).updateDb(eq(connection), changelogsCapture.capture(), tag = eq("cpk2"))
    }

    @Test
    fun `exception creating datasource is not caught`() {
        val dbConnectionManager = mock<DbConnectionManager>() {
            whenever(it.createDatasource(any())).thenThrow(IllegalArgumentException("some exception"))
        }
        val migrationUtility = MigrationUtilityImpl(dbConnectionManager, liquibaseSchemaMigrator)

        assertThrows<IllegalArgumentException> {
            migrationUtility.runVaultMigrations(
                ShortHash.Companion.of("123456789011"),
                listOf(cpk1DogsChangelog, cpk1CatsChangelog, cpk2RabbitsChangelog, cpk2SnakesChangelog),
                vaultDdlConnectionId
            )
        }
    }
}