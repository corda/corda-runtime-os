package net.corda.p2p.linkmanager.sessions

import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration
import net.corda.lifecycle.domino.logic.ConfigurationChangeHandler
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.schema.configuration.ConfigKeys.P2P_LINK_MANAGER_CONFIG
import java.util.concurrent.CompletableFuture

internal class DeadSessionMonitorConfigurationHandler(
    private val deadSessionMonitor: DeadSessionMonitor,
    configurationReaderService: ConfigurationReadService,
) : ConfigurationChangeHandler<Long>(
    configurationReaderService,
    P2P_LINK_MANAGER_CONFIG,
    { cfg -> cfg.getLong(LinkManagerConfiguration.SESSION_REFRESH_THRESHOLD_KEY) },
) {
    override fun applyNewConfiguration(
        newConfiguration: Long,
        oldConfiguration: Long?,
        resources: ResourcesHolder,
    ): CompletableFuture<Unit> {
        deadSessionMonitor.onConfigChange(newConfiguration)
        return CompletableFuture<Unit>().apply { complete(Unit) }
    }
}
