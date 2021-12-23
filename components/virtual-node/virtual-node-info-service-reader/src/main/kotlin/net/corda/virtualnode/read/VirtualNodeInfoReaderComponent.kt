package net.corda.virtualnode.read

import net.corda.lifecycle.Lifecycle

/**
 * Virtual node service component interface, particularly for OSGi
 */
interface VirtualNodeInfoReaderComponent : VirtualNodeInfoReader, Lifecycle
