package net.corda.virtualnode.component

import net.corda.lifecycle.Lifecycle
import net.corda.virtualnode.VirtualNodeInfoService

interface VirtualNodeInfoServiceComponent : VirtualNodeInfoService, Lifecycle
