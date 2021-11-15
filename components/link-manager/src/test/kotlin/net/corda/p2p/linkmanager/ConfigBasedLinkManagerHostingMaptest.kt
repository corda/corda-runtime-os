package net.corda.p2p.linkmanager

import com.typesafe.config.ConfigFactory
import net.corda.configuration.read.ConfigurationHandler
import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.SmartConfigImpl
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

class ConfigBasedLinkManagerHostingMaptest {

    private var listeners = mutableListOf<ConfigurationHandler>()
    private val latch = CountDownLatch(1)

    private val configReadService = mock<ConfigurationReadService> {
        on { registerForUpdates(any()) } doAnswer {invocation ->
            @Suppress("UNCHECKED_CAST")
            listeners.add(invocation.arguments[0] as ConfigurationHandler)
            latch.countDown()
            mock
        }
    }

    private val alice = LinkManagerNetworkMap.HoldingIdentity("O=Alice, L=London, C=GB", "group1")
    private val bob = LinkManagerNetworkMap.HoldingIdentity("O=Bob, L=London, C=GB", "group1")
    private val charlie = LinkManagerNetworkMap.HoldingIdentity("O=Charlie, L=London, C=GB", "group1")

    private val config = SmartConfigImpl(ConfigFactory.parseString(
        """
            ${LinkManagerConfiguration.LOCALLY_HOSTED_IDENTITIES_KEY}: [
                {
                    "${LinkManagerConfiguration.LOCALLY_HOSTED_IDENTITY_X500_NAME}": "${alice.x500Name}",
                    "${LinkManagerConfiguration.LOCALLY_HOSTED_IDENTITY_GPOUP_ID}": "${alice.groupId}"
                },
                {
                    "${LinkManagerConfiguration.LOCALLY_HOSTED_IDENTITY_X500_NAME}": "${bob.x500Name}",
                    "${LinkManagerConfiguration.LOCALLY_HOSTED_IDENTITY_GPOUP_ID}": "${bob.groupId}"
                }
            ]
        """
    ))

    private val hostingMap = ConfigBasedLinkManagerHostingMap(configReadService)

    @Test
    fun `locally hosted identities received via configuration are parsed properly and advised on lookups`() {
        val hostingMapStarted = thread { hostingMap.start() }
        latch.await()

        listeners.forEach { listener ->
            listener.onNewConfiguration(setOf(LinkManagerConfiguration.CONFIG_KEY), mapOf(
                LinkManagerConfiguration.CONFIG_KEY to config
            ))
        }

        hostingMapStarted.join()

        assertThat(hostingMap.isRunning).isTrue
        assertThat(hostingMap.isHostedLocally(alice)).isTrue
        assertThat(hostingMap.isHostedLocally(bob)).isTrue
        assertThat(hostingMap.isHostedLocally(charlie)).isFalse
    }

}