package net.corda.virtualnode.rest.impl.status

import net.corda.data.virtualnode.VirtualNodeOperationStatus
import net.corda.libs.configuration.SmartConfig
import net.corda.lifecycle.Lifecycle

interface VirtualNodeStatusCacheService : Lifecycle {

    /**
     * Initialises the API implementation. This method may be called multiple times throughout the life
     * of the API.
     */
    fun onConfiguration(config: SmartConfig)

    fun getStatus(requestId: String): VirtualNodeOperationStatus?

    fun setStatus(requestId: String, newStatus: VirtualNodeOperationStatus)
}
