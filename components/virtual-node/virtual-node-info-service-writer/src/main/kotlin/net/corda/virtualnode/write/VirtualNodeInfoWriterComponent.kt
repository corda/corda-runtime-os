package net.corda.virtualnode.write

import net.corda.lifecycle.Lifecycle
import net.corda.virtualnode.service.VirtualNodeInfoWriter

interface VirtualNodeInfoWriterComponent : VirtualNodeInfoWriter, Lifecycle
