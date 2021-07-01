package net.corda.messaging.db.partition

import kotlin.math.abs

class PartitionAssignor {

    fun assign(key: ByteArray, numberOfPartitions: Int): Int {
        return abs(key.contentHashCode()) % numberOfPartitions + 1
    }

}