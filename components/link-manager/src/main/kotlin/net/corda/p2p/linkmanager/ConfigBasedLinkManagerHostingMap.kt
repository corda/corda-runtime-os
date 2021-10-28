package net.corda.p2p.linkmanager

import com.typesafe.config.Config
import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.CONFIG_KEY
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.LOCALLY_HOSTED_IDENTITIES_KEY
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.LOCALLY_HOSTED_IDENTITY_GPOUP_ID
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.LOCALLY_HOSTED_IDENTITY_X500_NAME
import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ConfigurationChangeHandler
import net.corda.lifecycle.domino.logic.DominoTile
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import java.util.concurrent.ConcurrentHashMap

/**
 * Reads the set of holding identities hosted locally from configuration.
 */
class ConfigBasedLinkManagerHostingMap(
    val configReadService: ConfigurationReadService,
    coordinatorFactory: LifecycleCoordinatorFactory
): LinkManagerHostingMap, Lifecycle {

    override val isRunning: Boolean
        get() = dominoTile.isRunning

    override fun start() {
        if (!isRunning) {
            dominoTile.start()
        }
    }

    override fun stop() {
        if (isRunning) {
            dominoTile.stop()
        }
    }

    private val dominoTile = DominoTile(this::class.java.simpleName, coordinatorFactory, configurationChangeHandler = HostingMapConfigurationChangeHandler())

    override fun getDominoTile(): DominoTile {
        return dominoTile
    }

    private val locallyHostedIdentities = ConcurrentHashMap.newKeySet<LinkManagerNetworkMap.HoldingIdentity>()

    override fun isHostedLocally(identity: LinkManagerNetworkMap.HoldingIdentity): Boolean {
        return locallyHostedIdentities.contains(identity)
    }

    inner class HostingMapConfigurationChangeHandler: ConfigurationChangeHandler<Set<LinkManagerNetworkMap.HoldingIdentity>>(
        configReadService,
        CONFIG_KEY,
        ::fromConfig,
    ) {
        override fun applyNewConfiguration(
            newConfiguration: Set<LinkManagerNetworkMap.HoldingIdentity>,
            oldConfiguration: Set<LinkManagerNetworkMap.HoldingIdentity>?,
            resources: ResourcesHolder
        ) {
            val oldIdentities = (oldConfiguration ?: emptySet())
            val identitiesToAdd = newConfiguration - oldIdentities
            val identitiesToRemove = oldIdentities - newConfiguration
            locallyHostedIdentities.removeAll(identitiesToRemove)
            locallyHostedIdentities.addAll(identitiesToAdd)
        }
    }

    fun fromConfig(config: Config): Set<LinkManagerNetworkMap.HoldingIdentity> {
        val holdingIdentitiesConfig = config.getConfigList(LOCALLY_HOSTED_IDENTITIES_KEY)
        if (holdingIdentitiesConfig == null) {
            dominoTile.configApplied(DominoTile.ConfigUpdateResult.Error(
                InvalidLinkManagerConfigException(
                    "Invalid LinkManager config. $LOCALLY_HOSTED_IDENTITIES_KEY was not present in the config.")
                )
            )
            return emptySet()
        }
        val holdingIdentities = holdingIdentitiesConfig.map { identityConfig ->
            val x500name = identityConfig.getString(LOCALLY_HOSTED_IDENTITY_X500_NAME)
            val groupId = identityConfig.getString(LOCALLY_HOSTED_IDENTITY_GPOUP_ID)
            LinkManagerNetworkMap.HoldingIdentity(x500name, groupId)
        }
        return holdingIdentities.toSet()
    }

    class InvalidLinkManagerConfigException(override val message: String): RuntimeException(message)
}