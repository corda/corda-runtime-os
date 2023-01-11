package net.corda.membership.p2p.helpers

import net.corda.messaging.api.records.Record
import net.corda.data.p2p.markers.AppMessageMarker
import net.corda.data.p2p.markers.TtlExpiredMarker
import java.util.UUID

class TtlIdsFactory {
    private companion object {
        const val TTL_ID_PREFIX = "corda.membership.decline.if.ttl-"
        const val DELIMITER = "-"
    }

    fun createId(key: String): String {
        return TTL_ID_PREFIX +
            key +
            DELIMITER +
            UUID.randomUUID()
                .toString()
                .replace("-", "")
    }

    fun extractKey(record: Record<String, AppMessageMarker>): String? {
        if (!record.key.startsWith(TTL_ID_PREFIX)) {
            return null
        }
        if (record.value?.marker !is TtlExpiredMarker) {
            return null
        }
        return record.key.removePrefix(TTL_ID_PREFIX).substringBeforeLast(DELIMITER)
    }
}
