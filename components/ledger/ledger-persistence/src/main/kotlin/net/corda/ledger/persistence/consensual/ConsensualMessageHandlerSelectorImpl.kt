package net.corda.ledger.persistence.consensual

import net.corda.data.ledger.persistence.LedgerPersistenceRequest
import net.corda.ledger.persistence.common.RequestHandler
import net.corda.persistence.common.ResponseFactory
import net.corda.sandboxgroupcontext.SandboxGroupContext
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [ConsensualMessageHandlerSelector::class])
class ConsensualMessageHandlerSelectorImpl @Activate constructor(
    @Reference(service = ResponseFactory::class)
    private val responseFactory: ResponseFactory,
) : ConsensualMessageHandlerSelector {

    override fun selectHandler(sandbox: SandboxGroupContext, request: LedgerPersistenceRequest): RequestHandler {

        // TODOs, the individual handlers need to be lifed out of ConsensualLedgerRequestHandler
        // and put in this selector.
        return ConsensualLedgerRequestHandler(
            sandbox,
            request,
            responseFactory,
        )
    }
}
