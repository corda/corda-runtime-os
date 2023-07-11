package net.corda.testing.driver.processor

import net.corda.messaging.api.records.Record

fun interface ExternalProcessor {
    fun processEvent(record: Record<*, *>): List<Record<*, *>>
}
