package net.corda.libs.configuration.datamodel.tests

import net.corda.db.core.DbPrivilege
import net.corda.libs.configuration.datamodel.DbConnectionConfig
import net.corda.libs.cpi.datamodel.CpiMetadataEntity
import net.corda.libs.virtualnode.datamodel.entities.HoldingIdentityEntity
import net.corda.libs.virtualnode.datamodel.entities.VirtualNodeEntity
import net.corda.orm.utils.transaction
import net.corda.test.util.TestRandom
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeState
import java.time.Instant
import java.util.*
import javax.persistence.EntityManagerFactory

internal object VNodeTestUtils {
    fun newVNode(
        entityManagerFactory: EntityManagerFactory,
        name: String,
        version: String,
        hash: String): VirtualNodeEntity {

        println("Creating VNode for testing: $name, $version, $hash")

        val cpiMetadata = newCpiMetadataEntity(name, version, hash)
        val holdingIdentity = newHoldingIdentityEntity(name)
        val virtualNode = VirtualNodeEntity(holdingIdentity, name, version, hash, VirtualNodeState.ACTIVE.name)

        entityManagerFactory.createEntityManager().transaction { em ->
            em.persist(newDbConnection(holdingIdentity.cryptoDDLConnectionId!!, DbPrivilege.DDL))
            em.persist(newDbConnection(holdingIdentity.cryptoDMLConnectionId!!, DbPrivilege.DML))
            em.persist(newDbConnection(holdingIdentity.vaultDDLConnectionId!!, DbPrivilege.DDL))
            em.persist(newDbConnection(holdingIdentity.vaultDMLConnectionId!!, DbPrivilege.DML))
            em.persist(newDbConnection(holdingIdentity.uniquenessDDLConnectionId!!, DbPrivilege.DDL))
            em.persist(newDbConnection(holdingIdentity.uniquenessDMLConnectionId!!, DbPrivilege.DML))
            em.persist(holdingIdentity)
        }
        entityManagerFactory.createEntityManager().transaction { em -> em.persist(cpiMetadata) }
        entityManagerFactory.createEntityManager().transaction { em -> return em.merge(virtualNode) }
    }

    fun newDbConnection(connectionId: UUID, privilege: DbPrivilege) =
        DbConnectionConfig(
            connectionId,
            "c-$connectionId",
            privilege,
            Instant.now(),
            "test",
            "test connection",
            "{}")

    fun newHoldingIdentityEntity(id: String): HoldingIdentityEntity {
        val hi = HoldingIdentity(
            MemberX500Name.parse("C=GB,L=London,O=$id"),
            "dummy")
        return HoldingIdentityEntity(
            holdingIdentityShortHash = hi.shortHash.value,
            holdingIdentityFullHash = hi.fullHash,
            x500Name = hi.x500Name.toString(),
            mgmGroupId = hi.groupId,
            vaultDDLConnectionId = UUID.randomUUID(),
            vaultDMLConnectionId = UUID.randomUUID(),
            cryptoDDLConnectionId = UUID.randomUUID(),
            cryptoDMLConnectionId = UUID.randomUUID(),
            uniquenessDDLConnectionId = UUID.randomUUID(),
            uniquenessDMLConnectionId = UUID.randomUUID(),
            hsmConnectionId = UUID.randomUUID()
        )
    }

    fun newCpiMetadataEntity(
        name: String,
        version: String,
        hash: String
    ) = CpiMetadataEntity(
        name = name,
        version = version,
        signerSummaryHash = hash,
        fileName = "file",
        fileChecksum = TestRandom.hex(24),
        groupPolicy = "group policy",
        groupId = "group ID",
        fileUploadRequestId = "request ID",
        cpks = emptySet()
    )
}