package net.corda.membership.impl.persistence.service.handler

import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.MutualTlsRemoveFromAllowedCertificates
import net.corda.membership.datamodel.MutualTlsAllowedClientCertificateEntity
import net.corda.virtualnode.toCorda

internal class MutualTlsRemoveFromAllowedCertificatesHandler(
    persistenceHandlerServices: PersistenceHandlerServices
) : BasePersistenceHandler<MutualTlsRemoveFromAllowedCertificates, Unit>(persistenceHandlerServices) {
    override fun invoke(
        context: MembershipRequestContext,
        request: MutualTlsRemoveFromAllowedCertificates
    ) {
        transaction(context.holdingIdentity.toCorda().shortHash) { em ->
            em.find(
                MutualTlsAllowedClientCertificateEntity::class.java,
                request.subject,
            )?.also {
                em.remove(it)
            }
        }
    }
}
