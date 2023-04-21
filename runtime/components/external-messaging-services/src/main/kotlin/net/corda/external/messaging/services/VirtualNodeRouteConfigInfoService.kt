package net.corda.external.messaging.services

interface VirtualNodeRouteConfigInfoService {
    fun registerCallback(listener: VirtualNodeRouteConfigInfoListener): AutoCloseable
}

