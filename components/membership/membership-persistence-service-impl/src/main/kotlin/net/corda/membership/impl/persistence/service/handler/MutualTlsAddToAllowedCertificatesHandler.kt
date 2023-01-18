package net.corda.membership.impl.persistence.service.handler

import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.MutualTlsAddToAllowedCertificates
import net.corda.membership.datamodel.MutualTlsAllowedClientCertificateEntity
import net.corda.virtualnode.toCorda

internal class MutualTlsAddToAllowedCertificatesHandler(
    persistenceHandlerServices: PersistenceHandlerServices
) : BasePersistenceHandler<MutualTlsAddToAllowedCertificates, Unit>(persistenceHandlerServices) {
    override fun invoke(
        context: MembershipRequestContext,
        request: MutualTlsAddToAllowedCertificates
    ) {
        transaction(context.holdingIdentity.toCorda().shortHash) { em ->
            em.merge(
                MutualTlsAllowedClientCertificateEntity(
                    request.subject,
                )
            )
        }
    }
}
