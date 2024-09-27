package net.corda.ledger.persistence.utxo.impl

import net.corda.db.core.utils.BatchPersistenceServiceImpl
import net.corda.ledger.common.data.transaction.factory.WireTransactionFactory
import net.corda.ledger.libs.persistence.utxo.UtxoRepository
import net.corda.ledger.libs.persistence.utxo.impl.UtxoQueryProvider
import net.corda.ledger.libs.persistence.utxo.impl.UtxoRepositoryImpl
import net.corda.sandbox.type.SandboxConstants.CORDA_MARKER_ONLY_SERVICE
import net.corda.sandbox.type.UsedByPersistence
import net.corda.v5.application.serialization.SerializationService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope.PROTOTYPE

/**
 * Reads and writes ledger transaction data to and from the virtual node vault database.
 * The component only exists to be created inside a PERSISTENCE sandbox. We denote it
 * as "corda.marker.only" to force the sandbox to create it, despite it implementing
 * only the [UsedByPersistence] marker interface.
 */
@Component(
    service = [ UtxoRepository::class, UsedByPersistence::class ],
    property = [ CORDA_MARKER_ONLY_SERVICE ],
    scope = PROTOTYPE
)
class UtxoRepositoryOsgiImpl(delegate: UtxoRepository) : UtxoRepository by delegate, UsedByPersistence {
    @Suppress("Unused")
    @Activate
    constructor(
        @Reference(service = SerializationService::class)
        serializationService: SerializationService,
        @Reference(service = WireTransactionFactory::class)
        wireTransactionFactory: WireTransactionFactory,
        @Reference(service = UtxoQueryProvider::class)
        queryProvider: UtxoQueryProvider
    ) : this(UtxoRepositoryImpl(BatchPersistenceServiceImpl(), serializationService, wireTransactionFactory, queryProvider))
}
