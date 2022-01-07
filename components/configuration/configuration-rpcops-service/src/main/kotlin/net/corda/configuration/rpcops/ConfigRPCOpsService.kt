package net.corda.configuration.rpcops

import net.corda.lifecycle.Lifecycle

/** Manages the RPC operations that allow cluster configuration to be updated via the HTTP RPC gateway. */
interface ConfigRPCOpsService : Lifecycle