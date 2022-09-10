package net.corda.processors.ledger.impl

/**
 *  Keys to look up the per entity sandbox objects.
 */
object ConsensualLedgerContextTypes {
    const val SANDBOX_SERIALIZER = "AMQP_SERIALIZER"
    const val SANDBOX_EMF = "ENTITY_MANAGER_FACTORY"
}
