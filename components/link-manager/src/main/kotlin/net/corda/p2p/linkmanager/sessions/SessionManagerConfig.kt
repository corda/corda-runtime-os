package net.corda.p2p.linkmanager.sessions

import com.typesafe.config.Config
import net.corda.configuration.read.ConfigurationReadService
import net.corda.data.p2p.crypto.protocol.RevocationCheckMode
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration
import net.corda.lifecycle.domino.logic.ConfigurationChangeHandler
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import net.corda.schema.configuration.ConfigKeys
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference

internal class SessionManagerConfig(
    configurationReaderService: ConfigurationReadService,
) :
    ConfigurationChangeHandler<SessionManagerConfig.Configuration>(
        configurationReaderService,
        ConfigKeys.P2P_LINK_MANAGER_CONFIG,
        ::fromConfig,
    ) {
    private companion object {
        private fun fromConfig(config: Config): Configuration {
            return Configuration(
                config.getInt(LinkManagerConfiguration.MAX_MESSAGE_SIZE_KEY),
                if (config.getIsNull(LinkManagerConfiguration.SESSIONS_PER_PEER_KEY)) {
                    config.getInt(LinkManagerConfiguration.SESSIONS_PER_PEER_FOR_MEMBER_KEY)
                } else {
                    config.getInt(LinkManagerConfiguration.SESSIONS_PER_PEER_KEY)
                },
                config.getInt(LinkManagerConfiguration.SESSIONS_PER_PEER_FOR_MGM_KEY),
                config.getEnum(RevocationCheckMode::class.java, LinkManagerConfiguration.REVOCATION_CHECK_KEY),
                config.getInt(LinkManagerConfiguration.SESSION_REFRESH_THRESHOLD_KEY),
            )
        }
    }

    interface ConfigurationChanged {
        fun changed()
    }

    class Configuration(
        val maxMessageSize: Int,
        val sessionsPerPeerForMembers: Int,
        val sessionsPerPeerForMgm: Int,
        val revocationConfigMode: RevocationCheckMode,
        val sessionRefreshThreshold: Int,
    )
    // This default needs to be removed and the lifecycle dependency graph adjusted to ensure the inbound subscription starts only after
    // the configuration has been received and the session manager has started (see CORE-6730).
    private val config = AtomicReference(
        Configuration(
            1000000,
            2,
            1,
            RevocationCheckMode.OFF,
            432000,
        )
    )

    fun get() : Configuration = config.get()
    fun listen(listener: ConfigurationChanged) {
        onNewConfiguration.set(listener)
    }

    private val onNewConfiguration =  AtomicReference<ConfigurationChanged>()

    override fun applyNewConfiguration(
        newConfiguration: Configuration,
        oldConfiguration: Configuration?,
        resources: ResourcesHolder,
    ): CompletableFuture<Unit> {
        config.set(newConfiguration)
        if (oldConfiguration != null) {
            onNewConfiguration.get()?.changed()
        }
        return CompletableFuture.completedFuture(Unit)
    }

}
