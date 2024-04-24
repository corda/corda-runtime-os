package net.corda.membership.impl.persistence.service.handler

import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.PersistHostedIdentity
import net.corda.data.membership.db.response.command.PersistHostedIdentityResponse
import net.corda.membership.datamodel.HostedIdentityEntity
import net.corda.membership.datamodel.HostedIdentitySessionKeyInfoEntity
import net.corda.membership.lib.exceptions.MembershipPersistenceException
import net.corda.virtualnode.toCorda
import javax.persistence.LockModeType

internal class PersistHostedIdentityHandler(persistenceHandlerServices: PersistenceHandlerServices) :
    BasePersistenceHandler<PersistHostedIdentity, PersistHostedIdentityResponse>(persistenceHandlerServices) {
    override val operation = PersistHostedIdentity::class.java

    override fun invoke(context: MembershipRequestContext, request: PersistHostedIdentity): PersistHostedIdentityResponse {
        return transaction { em ->
            val holdingIdentityShortHash = context.holdingIdentity.toCorda().shortHash.value
            val currentVersion = em.find(
                HostedIdentityEntity::class.java,
                holdingIdentityShortHash,
                LockModeType.PESSIMISTIC_WRITE
            )?.version
            if (currentVersion != null) {
                // Overwrite entries in HostedIdentitySessionKeyInfoEntity
                em.createQuery(
                    "DELETE FROM ${HostedIdentitySessionKeyInfoEntity::class.java.simpleName} k " +
                        "WHERE k.holding_identity_id = $holdingIdentityShortHash"
                ).executeUpdate()
            }
            request.sessionKeysAndCertificates.forEach {
                em.persist(
                    HostedIdentitySessionKeyInfoEntity(
                        holdingIdentityShortHash,
                        it.sessionKeyId,
                        it.certificateAlias
                    )
                )
            }
            val preferredSessionKeyAndCert = request.sessionKeysAndCertificates.firstOrNull { it.isPreferred }?.sessionKeyId
                ?: throw MembershipPersistenceException(
                    "Failed to persist hosted identity for ${context.holdingIdentity}. No preferred session key was selected."
                )
            val newVersion = currentVersion?.inc() ?: 1
            val newEntity = HostedIdentityEntity(
                holdingIdentityShortHash,
                preferredSessionKeyAndCert,
                request.tlsCertificateAlias,
                request.useClusterLevelTls,
                newVersion
            )
            em.merge(newEntity)
            PersistHostedIdentityResponse(newVersion)
        }
    }
}
