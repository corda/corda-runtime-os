package net.corda.p2p.linkmanager

import com.typesafe.config.Config
import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.CONFIG_KEY
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.LOCALLY_HOSTED_IDENTITIES_KEY
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.LOCALLY_HOSTED_IDENTITY_GPOUP_ID
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.LOCALLY_HOSTED_IDENTITY_X500_NAME
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ConfigurationChangeHandler
import java.util.concurrent.ConcurrentHashMap

/**
 * Reads the set of holding identities hosted locally from configuration.
 */
class ConfigBasedLinkManagerHostingMap(
    configReadService: ConfigurationReadService,
    coordinatorFactory: LifecycleCoordinatorFactory): LinkManagerHostingMap,
    ConfigurationChangeHandler<Set<LinkManagerNetworkMap.HoldingIdentity>>(
        coordinatorFactory,
        configReadService,
        CONFIG_KEY,
        ::fromConfig
    ) {

    private val locallyHostedIdentities = ConcurrentHashMap.newKeySet<LinkManagerNetworkMap.HoldingIdentity>()

    override fun isHostedLocally(identity: LinkManagerNetworkMap.HoldingIdentity): Boolean {
        return locallyHostedIdentities.contains(identity)
    }

    override fun applyNewConfiguration(
        newConfiguration: Set<LinkManagerNetworkMap.HoldingIdentity>,
        oldConfiguration: Set<LinkManagerNetworkMap.HoldingIdentity>?
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
        ?: throw InvalidLinkManagerConfigException(
            "Invalid LinkManager config. $LOCALLY_HOSTED_IDENTITIES_KEY was not present in the config."
        )
    val holdingIdentities = holdingIdentitiesConfig.map { identityConfig ->
        val x500name = identityConfig.getString(LOCALLY_HOSTED_IDENTITY_X500_NAME)
        val groupId = identityConfig.getString(LOCALLY_HOSTED_IDENTITY_GPOUP_ID)
        LinkManagerNetworkMap.HoldingIdentity(x500name, groupId)
    }
    return holdingIdentities.toSet()
}