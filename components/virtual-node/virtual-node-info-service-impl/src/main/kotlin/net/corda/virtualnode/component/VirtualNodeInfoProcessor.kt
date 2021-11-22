package net.corda.virtualnode.component

import net.corda.configuration.read.ConfigurationHandler
import net.corda.lifecycle.Lifecycle
import net.corda.virtualnode.VirtualNodeInfoService

/**
 * Composite interface for the compacted queue processor.
 */
interface VirtualNodeInfoProcessor : VirtualNodeInfoService, ConfigurationHandler, Lifecycle
