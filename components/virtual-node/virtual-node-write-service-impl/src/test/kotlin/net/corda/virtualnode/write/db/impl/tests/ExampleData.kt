package net.corda.virtualnode.write.db.impl.tests

import net.corda.crypto.core.SecureHashImpl
import net.corda.crypto.core.parseSecureHash
import net.corda.data.virtualnode.VirtualNodeCreateRequest
import net.corda.db.connection.manager.VirtualNodeDbType
import net.corda.db.core.DbPrivilege
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.write.db.impl.writer.CpiMetadataLite
import net.corda.virtualnode.write.db.impl.writer.DbConnection
import net.corda.virtualnode.write.db.impl.writer.VirtualNodeDb
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import net.corda.data.identity.HoldingIdentity as AvroHoldingIdentity

internal const val ALICE_X500 = "CN=Alice, O=Alice Corp, L=LDN, C=GB"
internal const val GROUP_ID1 = "GROUP_ID1"
internal const val GROUP_POLICY1 = "GROUP_POLICY"
internal val ALICE_X500_NAME = MemberX500Name.parse(ALICE_X500)
internal val ALICE_HOLDING_ID1 = HoldingIdentity(ALICE_X500_NAME, GROUP_ID1)

internal const val MGM_X500 = "CN=MGM, O=Alice Corp, L=LDN, C=GB"
internal val MGM_X500_NAME = MemberX500Name.parse(MGM_X500)
internal val MGM_HOLDING_ID1 = HoldingIdentity(MGM_X500_NAME, GROUP_ID1)

internal const val CPI_NAME1 = "CPI1"
internal const val CPI_VERSION1 = "1.0"
internal val CPI_CHECKSUM1 = SecureHashImpl("SHA-256","CPI_CHECKSUM1".toByteArray())
internal val CPI_SIGNER_HASH1 = parseSecureHash("SHA-256:1234567890123456")
internal val CPI_IDENTIFIER1 = CpiIdentifier(CPI_NAME1, CPI_VERSION1, CPI_SIGNER_HASH1)
internal val CPI_METADATA1 = CpiMetadataLite(CPI_IDENTIFIER1, CPI_CHECKSUM1, GROUP_ID1, GROUP_POLICY1)

internal fun getValidRequest(): VirtualNodeCreateRequest {
    return VirtualNodeCreateRequest().apply {
        holdingId = AvroHoldingIdentity(ALICE_X500, GROUP_ID1)
        cpiFileChecksum = CPI_CHECKSUM1.toString()

        vaultDdlConnection = "vaultDdlConnection"
        vaultDmlConnection = "vaultDmlConnection"

        cryptoDdlConnection = "cryptoDdlConnection"
        cryptoDmlConnection = "cryptoDmlConnection"

        uniquenessDdlConnection = "uniquenessDdlConnection"
        uniquenessDmlConnection = "uniquenessDmlConnection"

        updateActor = "updateActor"
    }
}

internal fun getVNodeDb(
    dbType: VirtualNodeDbType,
    isPlatformManagedDb: Boolean = true,
    ddlConnection: DbConnection = mock(),
    dmlConnection: DbConnection = mock(),
): VirtualNodeDb {
    return  mock<VirtualNodeDb>().apply {
        whenever(this.isPlatformManagedDb).thenReturn(isPlatformManagedDb)
        whenever(this.dbType).thenReturn(dbType)
        whenever(this.dbConnections).thenReturn(
            mapOf(
                DbPrivilege.DDL to ddlConnection,
                DbPrivilege.DML to dmlConnection,
            )
        )
    }
}

internal fun getDbConnection(name: String, description: String, config: SmartConfig = mock()): DbConnection {
    return mock<DbConnection>().apply {
        whenever(this.name).thenReturn(name)
        whenever(this.description).thenReturn(description)
        whenever(this.config).thenReturn(config)
    }
}
