package net.corda.ledger.persistence.consensual.impl

import net.corda.data.ledger.persistence.FindTransaction
import net.corda.data.ledger.persistence.LedgerPersistenceRequest
import net.corda.data.ledger.persistence.LedgerTypes
import net.corda.data.ledger.persistence.PersistTransaction
import net.corda.ledger.persistence.common.RequestHandler
import net.corda.ledger.persistence.common.UnsupportedRequestTypeException
import net.corda.ledger.persistence.consensual.ConsensualRepository
import net.corda.ledger.persistence.consensual.ConsensualRequestHandlerSelector
import net.corda.persistence.common.ResponseFactory
import net.corda.persistence.common.getEntityManagerFactory
import net.corda.persistence.common.getSerializationService
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.getSandboxSingletonService
import net.corda.utilities.time.UTCClock
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [ConsensualRequestHandlerSelector::class])
class ConsensualRequestHandlerSelectorImpl @Activate constructor(
    @Reference(service = ResponseFactory::class)
    private val responseFactory: ResponseFactory,
) : ConsensualRequestHandlerSelector {

    override fun selectHandler(sandbox: SandboxGroupContext, request: LedgerPersistenceRequest): RequestHandler {
        val repository = sandbox.getSandboxSingletonService<ConsensualRepository>()
        val persistenceService = ConsensualPersistenceServiceImpl(
            sandbox.getEntityManagerFactory().createEntityManager(),
            repository,
            sandbox.getSandboxSingletonService(),
            UTCClock()
        )
        when (val req = request.request) {
            is FindTransaction -> {
                return ConsensualFindTransactionRequestHandler(
                    req,
                    sandbox.getSerializationService(),
                    request.flowExternalEventContext,
                    persistenceService,
                    responseFactory
                )
            }
            is PersistTransaction -> {
                return ConsensualPersistTransactionRequestHandler(
                    req,
                    sandbox.getSerializationService(),
                    request.flowExternalEventContext,
                    persistenceService,
                    responseFactory
                )
            }
            else -> {
                throw UnsupportedRequestTypeException(LedgerTypes.CONSENSUAL, request.request.javaClass)
            }
        }
    }
}
