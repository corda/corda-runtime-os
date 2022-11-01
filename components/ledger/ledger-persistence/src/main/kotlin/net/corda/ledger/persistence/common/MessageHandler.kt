package net.corda.ledger.persistence.common

import net.corda.messaging.api.records.Record

interface MessageHandler {
    fun execute(): List<Record<*, *>>
}