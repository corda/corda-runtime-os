package net.corda.ledger.consensual.persistence.internal

import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.ledger.consensual.impl.transaction.ConsensualSignedTransactionImpl
import net.corda.ledger.consensual.persistence.LedgerPersistenceClient
import net.corda.ledger.consensual.persistence.external.events.FindTransactionExternalEventFactory
import net.corda.ledger.consensual.persistence.external.events.FindTransactionParameters
import net.corda.ledger.consensual.persistence.external.events.PersistTransactionExternalEventFactory
import net.corda.ledger.consensual.persistence.external.events.PersistTransactionParameters
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.application.serialization.deserialize
import net.corda.v5.crypto.SecureHash
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope
import java.nio.ByteBuffer

@Component(service = [LedgerPersistenceClient::class, SingletonSerializeAsToken::class], scope = ServiceScope.PROTOTYPE)
class LedgerPersistenceClientImpl @Activate constructor(
    @Reference(service = ExternalEventExecutor::class)
    private val externalEventExecutor: ExternalEventExecutor,
    @Reference(service = SerializationService::class)
    private val serializationService: SerializationService
) : LedgerPersistenceClient, SingletonSerializeAsToken {

    override fun find(id: SecureHash): ConsensualSignedTransactionImpl? {
        return wrapWithPersistenceException {
            externalEventExecutor.execute(
                FindTransactionExternalEventFactory::class.java,
                FindTransactionParameters(id.toHexString())
            )
        }.firstOrNull()?.let { serializationService.deserialize(it.array()) }
    }

    override fun persist(transaction: ConsensualSignedTransactionImpl) {
        wrapWithPersistenceException {
            externalEventExecutor.execute(
                PersistTransactionExternalEventFactory::class.java,
                PersistTransactionParameters(serialize(transaction))
            )
        }
    }

    private fun serialize(payload: Any): ByteBuffer {
        return ByteBuffer.wrap(serializationService.serialize(payload).bytes)
    }
}