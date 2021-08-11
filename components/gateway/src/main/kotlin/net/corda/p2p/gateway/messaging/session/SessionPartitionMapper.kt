package net.corda.p2p.gateway.messaging.session

import net.corda.lifecycle.Lifecycle

/**
 * Provides the mapping from a session to the corresponding topic partitions.
 *
 * This is used to route messages for a session to the partitions, where that specific session is "hosted".
 */
interface SessionPartitionMapper: Lifecycle {
    /**
     * Returns the partitions for that specified session, or null if there's no mapping for that session.
     */
    fun getPartitions(sessionId: String): List<Int>?
}