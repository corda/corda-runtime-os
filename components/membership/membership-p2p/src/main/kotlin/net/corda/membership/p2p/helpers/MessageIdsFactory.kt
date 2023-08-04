package net.corda.membership.p2p.helpers

import net.corda.messaging.api.records.Record
import net.corda.data.p2p.markers.AppMessageMarker
import net.corda.data.p2p.markers.TtlExpiredMarker
import java.util.UUID

open class MessageIdsFactory(
    private val prefix: String,
) {
    private companion object {
        const val DELIMITER = "-"
    }

    fun createId(key: String): String {
        return prefix +
            key +
            DELIMITER +
            UUID.randomUUID()
                .toString()
                .replace("-", "")
    }

    fun extractKey(record: Record<String, AppMessageMarker>): String? {
        if (!record.key.startsWith(prefix)) {
            return null
        }
        if (record.value?.marker !is TtlExpiredMarker) {
            return null
        }
        return record.key.removePrefix(prefix).substringBeforeLast(DELIMITER)
    }
}
