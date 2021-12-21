package net.corda.crypto.client

import net.corda.crypto.HSMLabelMap
import net.corda.data.crypto.config.HSMLabel
import net.corda.messaging.api.processor.CompactedProcessor
import net.corda.messaging.api.records.Record

class HSMLabelMapProcessor : CompactedProcessor<String, HSMLabel>, HSMLabelMap {
    @Volatile
    private var map = mapOf<String, HSMLabel>()

    override val keyClass: Class<String> = String::class.java

    override val valueClass: Class<HSMLabel> = HSMLabel::class.java

    override fun onSnapshot(currentData: Map<String, HSMLabel>) {
        map = currentData
    }

    override fun onNext(newRecord: Record<String, HSMLabel>, oldValue: HSMLabel?, currentData: Map<String, HSMLabel>) {
        map = currentData
    }

    override fun get(tenantId: String, category: String): String = map.getValue(tenantId + category).label
}