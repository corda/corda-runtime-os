package net.corda.p2p.linkmanager

import com.typesafe.config.Config
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.HEARTBEAT_MESSAGE_PERIOD_KEY
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.MAX_MESSAGE_SIZE_KEY
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.MESSAGE_REPLAY_PERIOD_KEY
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.PROTOCOL_MODE_KEY
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.SESSION_TIMEOUT_KEY
import net.corda.p2p.crypto.ProtocolMode
import java.lang.RuntimeException

data class LinkManagerConfig(
    val maxMessageSize: Int,
    val protocolModes: Set<ProtocolMode>,
    val messageReplayPeriodSecs: Long,
    val heartbeatMessagePeriodMilliSecs: Long,
    val sessionTimeoutMilliSecs: Long,
) {
    companion object {
        fun parseConfig(config: Config): LinkManagerConfig {
            val maxMessageSize = config.getInt(MAX_MESSAGE_SIZE_KEY)
            val protocolModes = config.getEnumList(ProtocolMode::class.java, PROTOCOL_MODE_KEY).toSet()
            val messageReplayPeriodSecs = config.getLong(MESSAGE_REPLAY_PERIOD_KEY)
            val heartbeatMessagePeriod = config.getLong(HEARTBEAT_MESSAGE_PERIOD_KEY)
            val sessionTimeoutMilliSecs = config.getLong(SESSION_TIMEOUT_KEY)
            return LinkManagerConfig(
                maxMessageSize,
                protocolModes,
                messageReplayPeriodSecs,
                heartbeatMessagePeriod,
                sessionTimeoutMilliSecs
            )
        }
    }
}

class InvalidLinkManagerConfigException(override val message: String): RuntimeException(message)