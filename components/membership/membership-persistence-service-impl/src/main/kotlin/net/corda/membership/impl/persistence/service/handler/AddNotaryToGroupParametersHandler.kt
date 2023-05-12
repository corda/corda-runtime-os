package net.corda.membership.impl.persistence.service.handler

import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.command.AddNotaryToGroupParameters
import net.corda.data.membership.db.response.command.PersistGroupParametersResponse
import net.corda.membership.db.lib.AddNotaryToGroupParametersService
import net.corda.virtualnode.toCorda

internal class AddNotaryToGroupParametersHandler(
    persistenceHandlerServices: PersistenceHandlerServices,
) : BasePersistenceHandler<AddNotaryToGroupParameters, PersistGroupParametersResponse>(persistenceHandlerServices) {
    private val service = AddNotaryToGroupParametersService(
        persistenceHandlerServices.clock,
        persistenceHandlerServices.memberInfoFactory,
        persistenceHandlerServices.cordaAvroSerializationFactory,
        persistenceHandlerServices.keyEncodingService,
    )

    override fun invoke(
        context: MembershipRequestContext,
        request: AddNotaryToGroupParameters,
    ): PersistGroupParametersResponse {
        val persistedGroupParameters = transaction(context.holdingIdentity.toCorda().shortHash) { em ->
            service.add(em, request.notary)
        }
        return PersistGroupParametersResponse(persistedGroupParameters)
    }
}
