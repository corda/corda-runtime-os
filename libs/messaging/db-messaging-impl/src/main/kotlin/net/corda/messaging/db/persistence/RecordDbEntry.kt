package net.corda.messaging.db.persistence

data class RecordDbEntry(val topic: String, val partition: Int, val offset: Long, val key: ByteArray, val value: ByteArray?) {
    @Suppress("ComplexMethod")
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RecordDbEntry

        if (topic != other.topic) return false
        if (partition != other.partition) return false
        if (offset != other.offset) return false
        if (!key.contentEquals(other.key)) return false
        if (value != null) {
            if (other.value == null) return false
            if (!value.contentEquals(other.value)) return false
        } else if (other.value != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = topic.hashCode()
        result = 31 * result + partition
        result = 31 * result + offset.hashCode()
        result = 31 * result + key.contentHashCode()
        result = 31 * result + (value?.contentHashCode() ?: 0)
        return result
    }
}
