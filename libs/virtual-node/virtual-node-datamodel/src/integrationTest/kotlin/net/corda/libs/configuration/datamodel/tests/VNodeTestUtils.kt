package net.corda.libs.configuration.datamodel.tests

import net.corda.db.core.DbPrivilege
import net.corda.libs.configuration.datamodel.DbConnectionConfig
import net.corda.libs.cpi.datamodel.CpiMetadataEntity
import net.corda.libs.virtualnode.datamodel.entities.HoldingIdentityEntity
import net.corda.libs.virtualnode.datamodel.entities.VirtualNodeEntity
import net.corda.libs.virtualnode.datamodel.entities.VirtualNodeOperationEntity
import net.corda.orm.utils.transaction
import net.corda.test.util.TestRandom
import net.corda.v5.base.types.MemberX500Name
import net.corda.virtualnode.HoldingIdentity
import java.time.Instant
import java.util.*
import javax.persistence.EntityManagerFactory

internal object VNodeTestUtils {
    fun newVNode(
        entityManagerFactory: EntityManagerFactory,
        name: String,
        version: String,
        hash: String,
        virtualNodeOperationEntity: VirtualNodeOperationEntity? = null,
        holdingIdentityEntity: HoldingIdentityEntity? = null
    ): VirtualNodeEntity {

        println("Creating VNode for testing: $name, $version, $hash")

        val cpiMetadata = newCpiMetadataEntity(name, version, hash)
        val holdingIdentity = holdingIdentityEntity ?: newHoldingIdentityEntity(name)
        val virtualNode = VirtualNodeEntity(
            holdingIdentity.holdingIdentityShortHash,
            holdingIdentity,
            name,
            version,
            hash,
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            operationInProgress = virtualNodeOperationEntity
        )

        entityManagerFactory.createEntityManager().transaction { em ->
            em.persist(newDbConnection(virtualNode.cryptoDDLConnectionId!!, DbPrivilege.DDL))
            em.persist(newDbConnection(virtualNode.cryptoDMLConnectionId!!, DbPrivilege.DML))
            em.persist(newDbConnection(virtualNode.vaultDDLConnectionId!!, DbPrivilege.DDL))
            em.persist(newDbConnection(virtualNode.vaultDMLConnectionId!!, DbPrivilege.DML))
            em.persist(newDbConnection(virtualNode.uniquenessDDLConnectionId!!, DbPrivilege.DDL))
            em.persist(newDbConnection(virtualNode.uniquenessDMLConnectionId!!, DbPrivilege.DML))
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