package net.corda.membership.impl.persistence.service.handler

import net.corda.data.membership.db.request.query.QueryRegistrationRequest
import net.corda.data.membership.db.response.query.RegistrationRequestQueryResponse

internal class QueryQueuedRegistrationRequestsHandler(persistenceHandlerServices: PersistenceHandlerServices)
    : BaseRequestStatusHandler<QueryRegistrationRequest, RegistrationRequestQueryResponse>(persistenceHandlerServices) {

}