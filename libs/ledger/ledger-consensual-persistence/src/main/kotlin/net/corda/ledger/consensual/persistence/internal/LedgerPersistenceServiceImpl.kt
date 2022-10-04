package net.corda.ledger.consensual.persistence.internal

import java.nio.ByteBuffer
import net.corda.flow.external.events.executor.ExternalEventExecutor
import net.corda.ledger.common.impl.transaction.WireTransaction
import net.corda.ledger.consensual.persistence.LedgerPersistenceService
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

@Component(service = [LedgerPersistenceService::class, SingletonSerializeAsToken::class], scope = ServiceScope.PROTOTYPE)
class LedgerPersistenceServiceImpl @Activate constructor(
    @Reference(service = ExternalEventExecutor::class)
    private val externalEventExecutor: ExternalEventExecutor,
    @Reference(service = SerializationService::class)
    private val serializationService: SerializationService
) : LedgerPersistenceService, SingletonSerializeAsToken {

    override fun find(id: SecureHash): WireTransaction? {
        return wrapWithPersistenceException {
            externalEventExecutor.execute(
                FindTransactionExternalEventFactory::class.java,
                FindTransactionParameters(id.toHexString())
            )
        }.firstOrNull()?.let { serializationService.deserialize(it.array()) }
    }

    override fun persist(transaction: WireTransaction) {
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