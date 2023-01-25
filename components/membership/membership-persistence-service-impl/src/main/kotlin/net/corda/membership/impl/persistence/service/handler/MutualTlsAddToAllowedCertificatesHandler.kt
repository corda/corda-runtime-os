package net.corda.membership.impl.persistence.service.handler

import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.MutualTlsAddToAllowedCertificates
import net.corda.data.p2p.mtls.AllowedCertificateSubject
import net.corda.membership.datamodel.MutualTlsAllowedClientCertificateEntity
import net.corda.virtualnode.toCorda

internal class MutualTlsAddToAllowedCertificatesHandler(
    persistenceHandlerServices: PersistenceHandlerServices
) : BasePersistenceHandler<MutualTlsAddToAllowedCertificates, Unit>(persistenceHandlerServices) {
    override fun invoke(
        context: MembershipRequestContext,
        request: MutualTlsAddToAllowedCertificates
    ) {
        val shortHash = context.holdingIdentity.toCorda().shortHash
        transaction(shortHash) { em ->
            em.merge(
                MutualTlsAllowedClientCertificateEntity(
                    request.subject,
                    false,
                )
            )
        }

        val entry = AllowedCertificateSubject(request.subject, context.holdingIdentity.groupId)
        allowedCertificatesReaderWriterService.put(
            entry,
            entry,
        )
    }
}
