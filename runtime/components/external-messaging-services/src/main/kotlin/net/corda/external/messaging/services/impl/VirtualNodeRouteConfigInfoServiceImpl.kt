package net.corda.external.messaging.services.impl

import net.corda.external.messaging.entities.VirtualNodeRouteKey
import net.corda.external.messaging.services.VirtualNodeRouteConfigInfoService
import net.corda.external.messaging.services.VirtualNodeRouteConfigInfoListener
import net.corda.libs.external.messaging.serialization.ExternalMessagingRouteConfigSerializer
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.read.VirtualNodeInfoListener
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

@Suppress("Unused")
@Component(service = [VirtualNodeRouteConfigInfoService::class])
class VirtualNodeRouteConfigInfoServiceImpl @Activate constructor(
    @Reference(service = ExternalMessagingRouteConfigSerializer::class)
    private val externalMessagingRouteConfigSerializer: ExternalMessagingRouteConfigSerializer,
    @Reference(service = VirtualNodeInfoReadService::class)
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService
) : VirtualNodeRouteConfigInfoService {

    override fun registerCallback(listener: VirtualNodeRouteConfigInfoListener): AutoCloseable {

        return virtualNodeInfoReadService.registerCallback(
            VirtualNodeInfoChangeConverter(
                externalMessagingRouteConfigSerializer,
                listener
            )
        )
    }

    private class VirtualNodeInfoChangeConverter(
        private val externalMessagingRouteConfigSerializer: ExternalMessagingRouteConfigSerializer,
        private val listener: VirtualNodeRouteConfigInfoListener
    ) : VirtualNodeInfoListener {

        override fun onUpdate(
            changedKeys: Set<HoldingIdentity>,
            currentSnapshot: Map<HoldingIdentity, VirtualNodeInfo>
        ) {
            listener.onUpdate(
                currentSnapshot
                    .filter { kvp -> kvp.value.externalMessagingRouteConfig != null }
                    .flatMap { kvp ->
                        val routeConfig = externalMessagingRouteConfigSerializer
                            .deserialize(kvp.value.externalMessagingRouteConfig!!)
                        routeConfig.currentRoutes.routes.map { route ->
                            VirtualNodeRouteKey(kvp.key.shortHash.toString(), route.channelName) to route
                        }
                    }.toMap()
            )
        }
    }
}
