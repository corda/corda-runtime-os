package net.corda.membership.impl.persistence.service.handler

import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.MutualTlsRemoveFromAllowedCertificates
import net.corda.data.p2p.mtls.MgmAllowedCertificateSubject
import net.corda.membership.datamodel.MutualTlsAllowedClientCertificateEntity
import net.corda.virtualnode.toCorda

internal class MutualTlsRemoveFromAllowedCertificatesHandler(
    persistenceHandlerServices: PersistenceHandlerServices
) : BasePersistenceHandler<MutualTlsRemoveFromAllowedCertificates, Unit>(persistenceHandlerServices) {
    override fun invoke(
        context: MembershipRequestContext,
        request: MutualTlsRemoveFromAllowedCertificates
    ) {
        val shortHash = context.holdingIdentity.toCorda().shortHash
        transaction(shortHash) { em ->
            em.merge(
                MutualTlsAllowedClientCertificateEntity(
                    request.subject,
                    true,
                )
            )
        }
        val entry = MgmAllowedCertificateSubject(request.subject, context.holdingIdentity.groupId)
        allowedCertificatesReaderWriterService.remove(
            entry,
        )
    }
}
