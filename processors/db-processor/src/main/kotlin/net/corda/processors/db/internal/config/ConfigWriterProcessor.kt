package net.corda.processors.db.internal.config

import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record

internal class ConfigWriterProcessor(private val newConfigHandler: (Record<String, String>) -> Unit) :
    CompactedProcessor<String, String> {

    override val keyClass = String::class.java
    override val valueClass = String::class.java

    override fun onSnapshot(currentData: Map<String, String>) = Unit

    override fun onNext(newRecord: Record<String, String>, oldValue: String?, currentData: Map<String, String>) =
        newConfigHandler(newRecord)
}