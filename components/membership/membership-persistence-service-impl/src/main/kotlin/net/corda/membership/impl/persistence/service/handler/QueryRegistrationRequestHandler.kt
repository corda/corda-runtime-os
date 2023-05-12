package net.corda.membership.impl.persistence.service.handler

import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.query.QueryRegistrationRequest
import net.corda.data.membership.db.response.query.RegistrationRequestQueryResponse
import net.corda.membership.db.lib.QueryRegistrationRequestService
import net.corda.virtualnode.toCorda

internal class QueryRegistrationRequestHandler(persistenceHandlerServices: PersistenceHandlerServices)
    :BaseRequestStatusHandler<QueryRegistrationRequest, RegistrationRequestQueryResponse>(persistenceHandlerServices)
{
        private val getter = QueryRegistrationRequestService(persistenceHandlerServices.cordaAvroSerializationFactory)
    override fun invoke(
        context: MembershipRequestContext,
        request: QueryRegistrationRequest,
    ): RegistrationRequestQueryResponse {
        val shortHash = context.holdingIdentity.toCorda().shortHash
        return transaction(shortHash) { em ->
            val details = getter.get(em, request.registrationRequestId)
            RegistrationRequestQueryResponse(details)
        }
    }

}
