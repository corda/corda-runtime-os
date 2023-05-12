package net.corda.membership.impl.persistence.service.handler

import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.PersistMemberInfo
import net.corda.membership.db.lib.PersistMemberInfoService
import net.corda.virtualnode.toCorda

internal class PersistMemberInfoHandler(
    persistenceHandlerServices: PersistenceHandlerServices,
) : BasePersistenceHandler<PersistMemberInfo, Unit>(persistenceHandlerServices) {

    private val persister = PersistMemberInfoService(
        persistenceHandlerServices.cordaAvroSerializationFactory,
        persistenceHandlerServices.clock,
        persistenceHandlerServices.memberInfoFactory,
    )

    override fun invoke(context: MembershipRequestContext, request: PersistMemberInfo) {
        if (request.members.isNotEmpty()) {
            transaction(context.holdingIdentity.toCorda().shortHash) { em ->
                persister.persist(
                    em,
                    request.members,
                )
            }
        }
    }
}
