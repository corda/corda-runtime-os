package net.corda.gradle.plugin.network

import net.corda.gradle.plugin.FunctionalBaseTest
import net.corda.sdk.network.config.NetworkConfig
import net.corda.sdk.network.config.NetworkConfig.Companion.MULTIPLE_MGM_ERROR_MESSAGE
import net.corda.gradle.plugin.exception.CordaRuntimeGradlePluginException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class NetworkConfigTest : FunctionalBaseTest() {

    @Test
    fun canParseStaticNetworkFile() {
        appendCordaRuntimeGradlePluginExtension(isStaticNetwork = true)
        val networkFile = getNetworkConfigFile()
        val networkConfig = NetworkConfig(configFilePath = networkFile.absolutePath)
        assertThat(networkConfig.vNodes).isNotEmpty
    }

    @Test
    fun mgmIsNotPresentInStaticNetworkFile() {
        appendCordaRuntimeGradlePluginExtension(isStaticNetwork = true)
        val networkFile = getNetworkConfigFile()
        val networkConfig = NetworkConfig(configFilePath = networkFile.absolutePath)
        assertThat(networkConfig.mgmNodeIsPresentInNetworkDefinition).isFalse
    }

    @Test
    fun canParseDynamicNetworkFile() {
        appendCordaRuntimeGradlePluginExtension(isStaticNetwork = false)
        val networkFile = getNetworkConfigFile()
        val networkConfig = NetworkConfig(configFilePath = networkFile.absolutePath)
        assertThat(networkConfig.vNodes).isNotEmpty
    }

    @Test
    fun mgmIsPresentInDynamicNetworkFile() {
        appendCordaRuntimeGradlePluginExtension(isStaticNetwork = false)
        val networkFile = getNetworkConfigFile()
        val networkConfig = NetworkConfig(configFilePath = networkFile.absolutePath)
        assertThat(networkConfig.mgmNodeIsPresentInNetworkDefinition).isTrue
    }

    @Test
    fun errorIfTwoMgmsInNetworkFile() {
        val inputText = """
            [
              {
                "x500Name" : "CN=MGM, OU=Test Dept, O=R3, L=London, C=GB",
                "cpi" : "MGM",
                "mgmNode" : "true"
              },
              {
                "x500Name" : "CN=MGM2, OU=Test Dept, O=R3, L=London, C=GB",
                "cpi" : "MGM",
                "mgmNode" : "true"
              }
           ]
        """.trimIndent()
        val networkFile = kotlin.io.path.createTempFile("doubleMgmNetworkConfig", ".json").also {
            it.toFile().deleteOnExit()
        }.toFile()
        networkFile.writeText(inputText)
        val em = assertThrows<IllegalArgumentException> {
            NetworkConfig(configFilePath = networkFile.absolutePath)
        }
        assertThat(em.message).isEqualTo(MULTIPLE_MGM_ERROR_MESSAGE)
    }

    @Test
    fun canFilterListToNonMgmStaticNetwork() {
        appendCordaRuntimeGradlePluginExtension(isStaticNetwork = true)
        val networkFile = getNetworkConfigFile()
        val networkConfig = NetworkConfig(configFilePath = networkFile.absolutePath)
        val nodesWhoArentMgm = networkConfig.getVNodesWhoAreNotMgm()
        assertThat(nodesWhoArentMgm).hasSize(5)
    }

    @Test
    fun canFilterListToNonMgmDynamicNetwork() {
        appendCordaRuntimeGradlePluginExtension(isStaticNetwork = false)
        val networkFile = getNetworkConfigFile()
        val networkConfig = NetworkConfig(configFilePath = networkFile.absolutePath)
        val nodesWhoArentMgm = networkConfig.getVNodesWhoAreNotMgm()
        assertThat(nodesWhoArentMgm).hasSize(5)
    }
}
