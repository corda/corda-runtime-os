package net.corda.virtualnode.read

import net.corda.lifecycle.Lifecycle
import net.corda.virtualnode.service.VirtualNodeInfoReader

/**
 * Virtual node service component interface, particularly for OSGi
 */
interface VirtualNodeInfoReaderComponent : VirtualNodeInfoReader, Lifecycle
