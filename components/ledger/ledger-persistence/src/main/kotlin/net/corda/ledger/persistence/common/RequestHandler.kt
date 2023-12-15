package net.corda.ledger.persistence.common

import net.corda.messaging.api.records.Record

interface RequestHandler {
    fun execute(): List<Record<*, *>>
}
