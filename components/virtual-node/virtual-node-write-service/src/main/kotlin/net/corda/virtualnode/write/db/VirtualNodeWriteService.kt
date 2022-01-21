package net.corda.virtualnode.write.db

import net.corda.lifecycle.Lifecycle

/**
 * Receives virtual node creation requests via RPC, creates the corresponding virtual node, persists it in the cluster
 * database, and publishes it to Kafka.
 */
interface VirtualNodeWriteService : Lifecycle