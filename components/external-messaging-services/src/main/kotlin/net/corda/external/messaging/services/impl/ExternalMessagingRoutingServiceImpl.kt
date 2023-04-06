package net.corda.external.messaging.services.impl

import net.corda.external.messaging.entities.VerifiedRoute
import net.corda.external.messaging.entities.VirtualNodeRouteKey
import net.corda.external.messaging.services.ExternalMessagingRoutingService
import net.corda.external.messaging.services.VirtualNodeRouteConfigInfoService
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.helper.getConfig
import net.corda.libs.external.messaging.entities.Route
import net.corda.messagebus.api.admin.Admin
import net.corda.messagebus.api.admin.builder.AdminBuilder
import net.corda.messagebus.api.configuration.AdminConfig
import net.corda.schema.configuration.ConfigKeys.MESSAGING_CONFIG
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Suppress("Unused")
@Component(service = [ExternalMessagingRoutingService::class])
class ExternalMessagingRoutingServiceImpl(
    private val adminBuilder: AdminBuilder,
    virtualNodeRouteConfigInfoService: VirtualNodeRouteConfigInfoService,
    private val toMessagingConfig: (Map<String, SmartConfig>) -> SmartConfig,
) : ExternalMessagingRoutingService {

    @Activate
    constructor(
        @Reference(service = AdminBuilder::class)
        adminBuilder: AdminBuilder,
        @Reference(service = VirtualNodeRouteConfigInfoService::class)
        virtualNodeRouteConfigInfoService: VirtualNodeRouteConfigInfoService,
    ) : this(
        adminBuilder,
        virtualNodeRouteConfigInfoService,
        { cfg -> cfg.getConfig(MESSAGING_CONFIG) }
    )

    init {
        virtualNodeRouteConfigInfoService.registerCallback { vNodeRoutes ->
            currentRoutes = vNodeRoutes
            rebuildCache()
        }
    }

    private val topicAdminLock = ReentrantLock()
    private var currentRoutes = mapOf<VirtualNodeRouteKey, Route>()
    private var cacheRoutes = mapOf<VirtualNodeRouteKey, VerifiedRoute>()
    private var topicAdmin: Admin? = null

    override fun onConfigChange(config: Map<String, SmartConfig>) {
        topicAdminLock.withLock {
            topicAdmin?.close()
            topicAdmin = adminBuilder.createAdmin(
                AdminConfig("Messaging Routing Service"),
                toMessagingConfig(config)
            )
        }

        rebuildCache()
    }

    override fun getRoute(holdingIdentity: String, channelName: String): VerifiedRoute? {
        val key = VirtualNodeRouteKey(holdingIdentity, channelName)
        return cacheRoutes[key]
    }

    private fun rebuildCache() {
        topicAdminLock.withLock {
            topicAdmin?.let { admin ->
                val availableTopics = admin.getTopics()
                cacheRoutes = currentRoutes.map { kvp ->
                    kvp.key to VerifiedRoute(kvp.value, availableTopics.contains(kvp.value.externalReceiveTopicName))
                }.toMap()
            }
        }
    }
}
