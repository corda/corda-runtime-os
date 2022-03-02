package net.corda.p2p.linkmanager

import com.typesafe.config.Config
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.identity.HoldingIdentity
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.CONFIG_KEY
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.LOCALLY_HOSTED_IDENTITIES_KEY
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.LOCALLY_HOSTED_IDENTITY_GPOUP_ID
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.LOCALLY_HOSTED_IDENTITY_TENANT_ID
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.LOCALLY_HOSTED_IDENTITY_X500_NAME
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.LOCALLY_HOSTED_TLS_CERTIFICATES
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.ConfigurationChangeHandler
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.p2p.linkmanager.LinkManagerNetworkMap.Companion.toHoldingIdentity
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

    private val locallyHostedIdentityToTenantId = ConcurrentHashMap<LinkManagerNetworkMap.HoldingIdentity, String>()

    override fun isHostedLocally(identity: LinkManagerNetworkMap.HoldingIdentity): Boolean {
        return locallyHostedIdentityToTenantId.containsKey(identity)
    }

    override fun getTenantId(identity: LinkManagerNetworkMap.HoldingIdentity) = locallyHostedIdentityToTenantId[identity]

    override fun registerListener(listener: HostingMapListener) {
        listeners += listener
    }

    inner class HostingMapConfigurationChangeHandler :
        ConfigurationChangeHandler<Collection<HostingMapListener.IdentityInfo>>(
            configReadService,
            CONFIG_KEY,
            ::fromConfig,
        ) {
        override fun applyNewConfiguration(
            newConfiguration: Collection<HostingMapListener.IdentityInfo>,
            oldConfiguration: Collection<HostingMapListener.IdentityInfo>?,
            resources: ResourcesHolder,
        ): CompletableFuture<Unit> {
            locallyHostedIdentityToTenantId.clear()
            newConfiguration.forEach { identity ->
                locallyHostedIdentityToTenantId[identity.holdingIdentity.toHoldingIdentity()] = identity.tenantId
                listeners.forEach { listener ->
                    listener.identityAdded(identity)
                }
            }
            return CompletableFuture.completedFuture(Unit)
        }
    }

    private fun fromConfig(config: Config): Collection<HostingMapListener.IdentityInfo> {
        val holdingIdentitiesConfig = config.getConfigList(LOCALLY_HOSTED_IDENTITIES_KEY)
            ?: throw InvalidLinkManagerConfigException(
                "Invalid LinkManager config. getConfigList with key = $LOCALLY_HOSTED_IDENTITIES_KEY returned null."
            )
        return holdingIdentitiesConfig.map { identityConfig ->
            val x500name = identityConfig.getString(LOCALLY_HOSTED_IDENTITY_X500_NAME)
            val groupId = identityConfig.getString(LOCALLY_HOSTED_IDENTITY_GPOUP_ID)
            val certificates = identityConfig.getStringList(LOCALLY_HOSTED_TLS_CERTIFICATES)
            val tenantId = identityConfig.getString(LOCALLY_HOSTED_IDENTITY_TENANT_ID)
            HostingMapListener.IdentityInfo(
                holdingIdentity = HoldingIdentity(x500name, groupId),
                tlsCertificates = certificates,
                tenantId = tenantId,
            )
        }
    }

    class InvalidLinkManagerConfigException(override val message: String) : RuntimeException(message)
}
