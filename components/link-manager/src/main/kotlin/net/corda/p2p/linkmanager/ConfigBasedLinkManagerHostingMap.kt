package net.corda.p2p.linkmanager

import com.typesafe.config.Config
import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.CONFIG_KEY
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.LOCALLY_HOSTED_IDENTITIES_KEY
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.LOCALLY_HOSTED_IDENTITY_GPOUP_ID
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.LOCALLY_HOSTED_IDENTITY_X500_NAME
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ConfigurationChangeHandler
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/**
 * Reads the set of holding identities hosted locally from configuration.
 */
class ConfigBasedLinkManagerHostingMap(
    val configReadService: ConfigurationReadService,
    coordinatorFactory: LifecycleCoordinatorFactory
): LinkManagerHostingMap {

    override val dominoTile = ComplexDominoTile(
        this::class.java.simpleName,
        coordinatorFactory,
        configurationChangeHandler = HostingMapConfigurationChangeHandler()
    )

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
            resources: ResourcesHolder,
        ): CompletableFuture<Unit> {
            val oldIdentities = (oldConfiguration ?: emptySet())
            val identitiesToAdd = newConfiguration - oldIdentities
            val identitiesToRemove = oldIdentities - newConfiguration
            locallyHostedIdentities.removeAll(identitiesToRemove)
            locallyHostedIdentities.addAll(identitiesToAdd)
            val configUpdateResult = CompletableFuture<Unit>()
            configUpdateResult.complete(Unit)
            return configUpdateResult
        }
    }

    private fun fromConfig(config: Config): Set<LinkManagerNetworkMap.HoldingIdentity> {
        val holdingIdentitiesConfig = config.getConfigList(LOCALLY_HOSTED_IDENTITIES_KEY)
            ?: throw InvalidLinkManagerConfigException(
                "Invalid LinkManager config. getConfigList with key = $LOCALLY_HOSTED_IDENTITIES_KEY returned null."
            )
        val holdingIdentities = holdingIdentitiesConfig.map { identityConfig ->
            val x500name = identityConfig.getString(LOCALLY_HOSTED_IDENTITY_X500_NAME)
            val groupId = identityConfig.getString(LOCALLY_HOSTED_IDENTITY_GPOUP_ID)
            LinkManagerNetworkMap.HoldingIdentity(x500name, groupId)
        }
        return holdingIdentities.toSet()
    }

    class InvalidLinkManagerConfigException(override val message: String): RuntimeException(message)
}