package net.corda.virtualnode.write.db

import net.corda.lifecycle.Lifecycle

/** Receives configuration updates via RPC, persists them to the cluster database, and publishes them to Kafka. */
interface VirtualNodeWriteService : Lifecycle