package net.corda.virtualnode.write.db

import net.corda.lifecycle.Lifecycle

/**
 * A service responsible for handling virtual node write requests, from both RPC and asynchronous message patterns.
 */
interface VirtualNodeWriteService : Lifecycle