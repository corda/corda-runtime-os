package net.corda.libs.configuration.datamodel.tests

import net.corda.db.core.DbPrivilege
import net.corda.libs.configuration.datamodel.DbConnectionConfig
import net.corda.libs.cpi.datamodel.repository.CpiMetadataRepository
import net.corda.libs.packaging.core.CpiIdentifier
import net.corda.libs.virtualnode.datamodel.entities.HoldingIdentityEntity
import net.corda.libs.virtualnode.datamodel.entities.OperationType
import net.corda.libs.virtualnode.datamodel.entities.VirtualNodeEntity
import net.corda.libs.virtualnode.datamodel.entities.VirtualNodeOperationEntity
import net.corda.libs.virtualnode.datamodel.entities.VirtualNodeOperationState
import net.corda.orm.utils.transaction
import net.corda.test.util.TestRandom
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.HoldingIdentity
import java.time.Instant
import java.util.UUID
import javax.persistence.EntityManagerFactory

internal object VNodeTestUtils {
    @Suppress("LongParameterList")
    fun newVNode(
        entityManagerFactory: EntityManagerFactory,
        cpiName: String,
        cpiVersion: String,
        cpiSignerSummaryHash: SecureHash,
        virtualNodeOperationEntity: VirtualNodeOperationEntity? = null,
        holdingIdentityEntity: HoldingIdentityEntity? = null,
        externalMessagingRouteConfig: String?,
        cpiMetadataRepository: CpiMetadataRepository
    ): VirtualNodeEntity {
        println("Creating VNode for testing: $cpiName, $cpiVersion, $cpiSignerSummaryHash")

        val holdingIdentity = holdingIdentityEntity ?: newHoldingIdentityEntity(cpiName)
        val virtualNode = VirtualNodeEntity(
            holdingIdentity.holdingIdentityShortHash,
            holdingIdentity,
            cpiName,
            cpiVersion,
            cpiSignerSummaryHash.toString(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            operationInProgress = virtualNodeOperationEntity,
            externalMessagingRouteConfig = externalMessagingRouteConfig
        )

        entityManagerFactory.createEntityManager().transaction { em ->
            em.persist(newDbConnection(virtualNode.cryptoDDLConnectionId!!, DbPrivilege.DDL))
            em.persist(newDbConnection(virtualNode.cryptoDMLConnectionId!!, DbPrivilege.DML))
            em.persist(newDbConnection(virtualNode.vaultDDLConnectionId!!, DbPrivilege.DDL))
            em.persist(newDbConnection(virtualNode.vaultDMLConnectionId!!, DbPrivilege.DML))
            em.persist(newDbConnection(virtualNode.uniquenessDDLConnectionId!!, DbPrivilege.DDL))
            em.persist(newDbConnection(virtualNode.uniquenessDMLConnectionId!!, DbPrivilege.DML))
        }

        entityManagerFactory.createEntityManager().transaction { em ->
            cpiMetadataRepository.put(
                em,
                CpiIdentifier(cpiName, cpiVersion, cpiSignerSummaryHash),
                cpiFileName = "file",
                fileChecksum = TestRandom.secureHash(24),
                groupPolicy = "group policy",
                groupId = "group ID",
                fileUploadRequestId = "request ID",
                cpks = emptySet()
            )
        }
        entityManagerFactory.createEntityManager().transaction { em -> return em.merge(virtualNode) }
    }

    fun newVNodeOperation(
        entityManagerFactory: EntityManagerFactory,
        requestId: String,
        data: String,
        state: VirtualNodeOperationState,
        operationType: OperationType
    ) {
        val operation = VirtualNodeOperationEntity(
            UUID.randomUUID().toString(),
            requestId,
            data,
            state,
            operationType,
            Instant.now(),
            Instant.now(),
            Instant.now(),
            null
        )

        entityManagerFactory.createEntityManager().transaction { em -> em.persist(operation) }
    }

    fun newDbConnection(connectionId: UUID, privilege: DbPrivilege) =
        DbConnectionConfig(
            connectionId,
            "c-$connectionId",
            privilege,
            Instant.now(),
            "test",
            "test connection",
            "{}"
        )

    fun newHoldingIdentityEntity(id: String): HoldingIdentityEntity {
        val hi = HoldingIdentity(
            MemberX500Name.parse("C=GB,L=London,O=$id"),
            "dummy"
        )
        return HoldingIdentityEntity(
            holdingIdentityShortHash = hi.shortHash.value,
            holdingIdentityFullHash = hi.fullHash,
            x500Name = hi.x500Name.toString(),
            mgmGroupId = hi.groupId,
            hsmConnectionId = UUID.randomUUID()
        )
    }
}
