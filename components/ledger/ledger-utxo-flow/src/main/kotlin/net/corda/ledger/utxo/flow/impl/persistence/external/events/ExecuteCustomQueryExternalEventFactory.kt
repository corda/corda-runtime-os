package net.corda.ledger.utxo.flow.impl.persistence.external.events

import net.corda.data.ledger.persistence.ExecuteVaultNamedQueryRequest
import net.corda.flow.external.events.factory.ExternalEventFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer

@Component(service = [ExternalEventFactory::class])
class VaultNamedQueryExternalEventFactory @Activate constructor():
    AbstractUtxoLedgerExternalEventFactory<VaultNamedQueryEventParams>() {

    private companion object {
        val log = LoggerFactory.getLogger(VaultNamedQueryExternalEventFactory::class.java)
    }

    override fun createRequest(parameters: VaultNamedQueryEventParams): Any {
        log.info("creating request in VaultNamedQueryExternalEventFactory")
        return ExecuteVaultNamedQueryRequest(
            parameters.queryName,
            parameters.queryParameters,
            parameters.offset,
            parameters.limit
        )
    }

}

data class VaultNamedQueryEventParams(
    val queryName: String,
    val queryParameters: Map<String, ByteBuffer>,
    val offset: Int,
    val limit: Int
)
