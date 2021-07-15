package net.corda.messaging.db.partition

import kotlin.math.abs

class PartitionAssignor {

    fun assign(key: ByteArray, numberOfPartitions: Int): Int {
        require(numberOfPartitions > 0)
        return abs(key.contentHashCode() % numberOfPartitions) + 1
    }

}