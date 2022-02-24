package net.corda.p2p.linkmanager

import com.typesafe.config.ConfigFactory
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration
import net.corda.lifecycle.domino.logic.ComplexDominoTile
import net.corda.lifecycle.domino.logic.util.ResourcesHolder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ConfigBasedLinkManagerHostingMapTest {

    private lateinit var configHandler: ConfigBasedLinkManagerHostingMap.HostingMapConfigurationChangeHandler
    private val dominoTile = Mockito.mockConstruction(ComplexDominoTile::class.java) { _, context ->
        @Suppress("UNCHECKED_CAST")
        configHandler = context.arguments()[5] as ConfigBasedLinkManagerHostingMap.HostingMapConfigurationChangeHandler
    }

    @AfterEach
    fun cleanUp() {
        dominoTile.close()
    }

    private val alice = LinkManagerNetworkMap.HoldingIdentity("O=Alice, L=London, C=GB", "group1")
    private val bob = LinkManagerNetworkMap.HoldingIdentity("O=Bob, L=London, C=GB", "group1")
    private val charlie = LinkManagerNetworkMap.HoldingIdentity("O=Charlie, L=London, C=GB", "group1")
    private val configResourcesHolder = mock<ResourcesHolder>()

    private val config = SmartConfigFactory.create(ConfigFactory.empty()).create(ConfigFactory.parseString(
        """
            ${LinkManagerConfiguration.LOCALLY_HOSTED_IDENTITIES_KEY}: [
                {
                    "${LinkManagerConfiguration.LOCALLY_HOSTED_IDENTITY_X500_NAME}": "${alice.x500Name}",
                    "${LinkManagerConfiguration.LOCALLY_HOSTED_IDENTITY_GROUP_ID}": "${alice.groupId}"
                },
                {
                    "${LinkManagerConfiguration.LOCALLY_HOSTED_IDENTITY_X500_NAME}": "${bob.x500Name}",
                    "${LinkManagerConfiguration.LOCALLY_HOSTED_IDENTITY_GROUP_ID}": "${bob.groupId}"
                }
            ]
        """
    ))

    private val hostingMap = ConfigBasedLinkManagerHostingMap(mock(), mock())

    @Test
    fun `locally hosted identities received via configuration are parsed properly and advised on lookups`() {
        setRunning()
        val typedConfig = configHandler.configFactory(config)
        val future = configHandler.applyNewConfiguration(typedConfig, null, configResourcesHolder)

        assertThat(hostingMap.isHostedLocally(alice)).isTrue
        assertThat(hostingMap.isHostedLocally(bob)).isTrue
        assertThat(hostingMap.isHostedLocally(charlie)).isFalse
        assertThat(future.isDone).isTrue
        assertThat(future.isCompletedExceptionally).isFalse
    }

    @Test
    fun `if config is invalid the config factory throws an exception`() {
        assertThrows<Exception> {
            configHandler.configFactory(ConfigFactory.parseString(""))
        }
    }

    private fun setRunning() {
        whenever(dominoTile.constructed().first().isRunning).doReturn(true)
    }
}