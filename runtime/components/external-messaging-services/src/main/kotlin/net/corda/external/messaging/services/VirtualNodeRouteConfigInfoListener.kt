package net.corda.external.messaging.services

import net.corda.external.messaging.entities.VirtualNodeRouteKey
import net.corda.libs.external.messaging.entities.Route

fun interface VirtualNodeRouteConfigInfoListener {
    fun onUpdate(currentSnapshot: Map<VirtualNodeRouteKey, Route>)
}
