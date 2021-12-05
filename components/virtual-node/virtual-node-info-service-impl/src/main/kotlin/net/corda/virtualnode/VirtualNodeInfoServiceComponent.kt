package net.corda.virtualnode

import net.corda.lifecycle.Lifecycle

/**
 * Virtual node service component interface, particularly for OSGi
 */
interface VirtualNodeInfoServiceComponent : VirtualNodeInfoService, Lifecycle
