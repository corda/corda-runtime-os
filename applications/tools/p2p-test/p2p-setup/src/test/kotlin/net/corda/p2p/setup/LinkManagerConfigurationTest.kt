package net.corda.p2p.setup

import com.typesafe.config.ConfigFactory
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.BASE_REPLAY_PERIOD_KEY
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.HEARTBEAT_MESSAGE_PERIOD_KEY
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.MAX_MESSAGE_SIZE_KEY
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.MAX_REPLAYING_MESSAGES_PER_PEER
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.REPLAY_PERIOD_CUTOFF_KEY
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.REPLAY_PERIOD_KEY
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.SESSIONS_PER_PEER_KEY
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.SESSION_TIMEOUT_KEY
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.ReplayAlgorithm.Constant
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.ReplayAlgorithm.ExponentialBackoff
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.Test


class LinkManagerConfigurationTest {

    companion object {
        const val TOPIC = "MyFavouriteTopic"
        const val MAX_MESSAGE_SIZE = 27
        val CONST_REPLAY_ALGORITHM = Constant
        val EXP_REPLAY_ALGORITHM = ExponentialBackoff
        const val MESSAGE_REPLAY_PERIOD = 2000L
        const val MESSAGE_REPLAY_PERIOD_BASE = 27L
        const val MESSAGE_REPLAY_CUT_OFF = 49L
        const val MAX_REPLAYING_MESSAGES = 100
        const val HEARTBEAT_MESSAGE_REPLAY_PERIOD = 200L
        const val SESSION_TIMEOUT = 10L
        const val SESSIONS_PER_PEER = 4L
    }


    @Test
    fun `LinkManagerConfiguration can be written with Constant replay algorithm`() {
        val linkManagerConfiguration = LinkManagerConfiguration().apply {
            topic = TOPIC
            maxMessageSize = MAX_MESSAGE_SIZE
            replayAlgorithm = CONST_REPLAY_ALGORITHM
            messageReplayPeriodMilliSecs = MESSAGE_REPLAY_PERIOD
            maxReplayingMessages = MAX_REPLAYING_MESSAGES
            heartbeatMessagePeriodMilliSecs = HEARTBEAT_MESSAGE_REPLAY_PERIOD
            sessionTimeoutMilliSecs = SESSION_TIMEOUT
            sessionsPerPeer = SESSIONS_PER_PEER
        }
        val record = linkManagerConfiguration.call().single()
        SoftAssertions.assertSoftly {
            it.assertThat(record.topic).isEqualTo(TOPIC)
            val config = ConfigFactory.parseString(record.value!!.value)
            it.assertThat(config.getValue(MAX_MESSAGE_SIZE_KEY).unwrapped()).isEqualTo(MAX_MESSAGE_SIZE)
            it.assertThat(config.getValue(MAX_REPLAYING_MESSAGES_PER_PEER).unwrapped()).isEqualTo(MAX_REPLAYING_MESSAGES)
            it.assertThat(config.getValue(HEARTBEAT_MESSAGE_PERIOD_KEY).unwrapped()).isEqualTo(HEARTBEAT_MESSAGE_REPLAY_PERIOD.toInt())
            it.assertThat(config.getValue(SESSION_TIMEOUT_KEY).unwrapped()).isEqualTo(SESSION_TIMEOUT.toInt())
            it.assertThat(config.getValue(SESSIONS_PER_PEER_KEY).unwrapped()).isEqualTo(SESSIONS_PER_PEER.toInt())
            val innerConfig = config.getConfig(Constant.configKeyName())
            it.assertThat(innerConfig.getValue(REPLAY_PERIOD_KEY).unwrapped()).isEqualTo(MESSAGE_REPLAY_PERIOD.toInt())
        }
    }

    @Test
    fun `LinkManagerConfiguration can be written with Exponential replay algorithm`() {
        val linkManagerConfiguration = LinkManagerConfiguration().apply {
            topic = TOPIC
            maxMessageSize = MAX_MESSAGE_SIZE
            replayAlgorithm = EXP_REPLAY_ALGORITHM
            messageReplayPeriodBaseMilliSecs = MESSAGE_REPLAY_PERIOD_BASE
            messageReplayPeriodCutoffMilliSecs = MESSAGE_REPLAY_CUT_OFF
            maxReplayingMessages = MAX_REPLAYING_MESSAGES
            heartbeatMessagePeriodMilliSecs = HEARTBEAT_MESSAGE_REPLAY_PERIOD
            sessionTimeoutMilliSecs = SESSION_TIMEOUT
            sessionsPerPeer = SESSIONS_PER_PEER
        }
        val record = linkManagerConfiguration.call().single()
        SoftAssertions.assertSoftly {
            it.assertThat(record.topic).isEqualTo(TOPIC)
            val config = ConfigFactory.parseString(record.value!!.value)
            it.assertThat(config.getValue(MAX_MESSAGE_SIZE_KEY).unwrapped()).isEqualTo(MAX_MESSAGE_SIZE)
            it.assertThat(config.getValue(MAX_REPLAYING_MESSAGES_PER_PEER).unwrapped()).isEqualTo(MAX_REPLAYING_MESSAGES)
            it.assertThat(config.getValue(HEARTBEAT_MESSAGE_PERIOD_KEY).unwrapped()).isEqualTo(HEARTBEAT_MESSAGE_REPLAY_PERIOD.toInt())
            it.assertThat(config.getValue(SESSION_TIMEOUT_KEY).unwrapped()).isEqualTo(SESSION_TIMEOUT.toInt())
            it.assertThat(config.getValue(SESSIONS_PER_PEER_KEY).unwrapped()).isEqualTo(SESSIONS_PER_PEER.toInt())
            val innerConfig = config.getConfig(ExponentialBackoff.configKeyName())
            it.assertThat(innerConfig.getValue(BASE_REPLAY_PERIOD_KEY).unwrapped()).isEqualTo(MESSAGE_REPLAY_PERIOD_BASE.toInt())
            it.assertThat(innerConfig.getValue(REPLAY_PERIOD_CUTOFF_KEY).unwrapped()).isEqualTo(MESSAGE_REPLAY_CUT_OFF.toInt())
        }
    }

}