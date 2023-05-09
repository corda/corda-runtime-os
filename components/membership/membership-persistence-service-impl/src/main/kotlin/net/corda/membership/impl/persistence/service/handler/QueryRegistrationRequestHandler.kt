package net.corda.membership.impl.persistence.service.handler

import net.corda.data.membership.db.request.MembershipRequestContext
import net.corda.data.membership.db.request.query.QueryRegistrationRequest
import net.corda.data.membership.db.response.query.RegistrationRequestQueryResponse
import net.corda.membership.datamodel.RegistrationRequestEntity
import net.corda.virtualnode.toCorda

internal class QueryRegistrationRequestHandler(persistenceHandlerServices: PersistenceHandlerServices) :
    BaseRequestStatusHandler<QueryRegistrationRequest, RegistrationRequestQueryResponse>(persistenceHandlerServices) {
    override fun invoke(
        context: MembershipRequestContext,
        request: QueryRegistrationRequest,
    ): RegistrationRequestQueryResponse {
        return transaction(context.holdingIdentity.toCorda().shortHash) { em ->
            RegistrationRequestQueryResponse(
                em.find(
                    RegistrationRequestEntity::class.java,
                    request.registrationRequestId
                )?.toDetails()
            )
        }
    }

}
