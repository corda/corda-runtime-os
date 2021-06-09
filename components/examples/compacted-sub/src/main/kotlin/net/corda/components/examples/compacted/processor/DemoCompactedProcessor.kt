package net.corda.components.examples.compacted.processor

import net.corda.data.demo.DemoRecord
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class DemoCompactedProcessor : CompactedProcessor<String, DemoRecord> {

    private companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    override val keyClass: Class<String>
        get() = String::class.java
    override val valueClass: Class<DemoRecord>
        get() = DemoRecord::class.java

    override fun onSnapshot(currentData: Map<String, DemoRecord>) {
        log.info("Compacted Processor: current data $currentData")
    }

    override fun onNext(newRecord: Record<String, DemoRecord>, oldValue: DemoRecord?, currentData: Map<String, DemoRecord>) {
        log.info("Compacted Processor: New record value ${newRecord.value}, old record value ${oldValue?.value}")
    }
}