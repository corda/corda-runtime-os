package net.corda.libs.configuration.read.impl.processor

import net.corda.data.config.Configuration
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record

class ConfigCompactedProcessor : CompactedProcessor<String, Configuration> {
    override val keyClass: Class<String>
        get() = TODO("Not yet implemented")
    override val valueClass: Class<Configuration>
        get() = TODO("Not yet implemented")

    override fun onSnapshot(currentData: Map<String, Configuration>) {
        TODO("Not yet implemented")
    }

    override fun onNext(
        newRecord: Record<String, Configuration>,
        oldValue: Configuration?,
        currentData: Map<String, Configuration>
    ) {
        TODO("Not yet implemented")
    }
}