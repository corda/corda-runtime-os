package net.corda.ledger.persistence.consensual

import net.corda.data.ledger.persistence.LedgerPersistenceRequest
import net.corda.ledger.persistence.common.MessageHandler
import net.corda.persistence.common.ResponseFactory
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.cipher.suite.DigestService
import net.corda.v5.cipher.suite.merkle.MerkleTreeProvider
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Component(service = [ConsensualMessageHandlerSelector::class])
class ConsensualMessageHandlerSelectorImpl @Activate constructor(
    @Reference(service = ResponseFactory::class)
    private val responseFactory: ResponseFactory,
    @Reference(service = MerkleTreeProvider::class)
    private val merkleTreeProvider: MerkleTreeProvider,
    @Reference(service = DigestService::class)
    private val digestService: DigestService,
    @Reference(service = JsonMarshallingService::class)
    private val jsonMarshallingService: JsonMarshallingService
) : ConsensualMessageHandlerSelector {

    override fun selectHandler(sandbox: SandboxGroupContext, request: LedgerPersistenceRequest): MessageHandler {

        // TODO, the individual handlers need to be lifed out of
        // ConsensualLedgerMessageHandler and put in this selector.
        return ConsensualLedgerMessageHandler(
            sandbox,
            request,
            responseFactory,
            merkleTreeProvider,
            digestService,
            jsonMarshallingService
        )
    }
}
