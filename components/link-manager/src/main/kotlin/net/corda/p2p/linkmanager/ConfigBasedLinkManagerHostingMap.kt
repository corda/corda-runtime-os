package net.corda.p2p.linkmanager

import com.typesafe.config.Config
import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.CONFIG_KEY
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.LOCALLY_HOSTED_IDENTITIES_KEY
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.LOCALLY_HOSTED_IDENTITY_GPOUP_ID
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.LOCALLY_HOSTED_IDENTITY_X500_NAME
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.LOCALLY_HOSTED_TLS_CERTIFICATES
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.ConfigurationChangeHandler
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/**
 * Reads the set of holding identities hosted locally from configuration.
 */
class ConfigBasedLinkManagerHostingMap(
    val configReadService: ConfigurationReadService,
    coordinatorFactory: LifecycleCoordinatorFactory,
) : LinkManagerHostingMap {

    private val listeners = ConcurrentHashMap.newKeySet<HostingMapListener>()

    override val dominoTile = ComplexDominoTile(
        this::class.java.simpleName,
        coordinatorFactory,
        configurationChangeHandler = HostingMapConfigurationChangeHandler()
    )

    private val locallyHostedIdentities = ConcurrentHashMap.newKeySet<LinkManagerNetworkMap.HoldingIdentity>()

    override fun isHostedLocally(identity: LinkManagerNetworkMap.HoldingIdentity): Boolean {
        return locallyHostedIdentities.contains(identity)
    }

    override fun registerListener(listener: HostingMapListener) {
        listeners += listener
    }

    inner class HostingMapConfigurationChangeHandler :
        ConfigurationChangeHandler<Map<LinkManagerNetworkMap.HoldingIdentity, List<PemCertificates>>>(
            configReadService,
            CONFIG_KEY,
            ::fromConfig,
        ) {
        override fun applyNewConfiguration(
            newConfiguration: Map<LinkManagerNetworkMap.HoldingIdentity, List<PemCertificates>>,
            oldConfiguration: Map<LinkManagerNetworkMap.HoldingIdentity, List<PemCertificates>>?,
            resources: ResourcesHolder,
        ): CompletableFuture<Unit> {
            val oldIdentities = (oldConfiguration?.keys ?: emptySet())
            val identitiesToAdd = newConfiguration.keys - oldIdentities
            val identitiesToRemove = oldIdentities - newConfiguration.keys
            locallyHostedIdentities.removeAll(identitiesToRemove)
            locallyHostedIdentities.addAll(identitiesToAdd)
            newConfiguration.map {
                HostingMapListener.IdentityInfo(it.key.toHoldingIdentity(), it.value)
            }.forEach { identity ->
                listeners.forEach { listener ->
                    listener.identityAdded(identity)
                }
            }
            return CompletableFuture.completedFuture(Unit)
        }
    }

    private fun fromConfig(config: Config): Map<LinkManagerNetworkMap.HoldingIdentity, List<PemCertificates>> {
        val holdingIdentitiesConfig = config.getConfigList(LOCALLY_HOSTED_IDENTITIES_KEY)
            ?: throw InvalidLinkManagerConfigException(
                "Invalid LinkManager config. getConfigList with key = $LOCALLY_HOSTED_IDENTITIES_KEY returned null."
            )
        return holdingIdentitiesConfig.map { identityConfig ->
            val x500name = identityConfig.getString(LOCALLY_HOSTED_IDENTITY_X500_NAME)
            val groupId = identityConfig.getString(LOCALLY_HOSTED_IDENTITY_GPOUP_ID)
            val certificates = identityConfig.getStringList(LOCALLY_HOSTED_TLS_CERTIFICATES)
            LinkManagerNetworkMap.HoldingIdentity(x500name, groupId) to certificates
        }.toMap()
    }

    class InvalidLinkManagerConfigException(override val message: String) : RuntimeException(message)
}
