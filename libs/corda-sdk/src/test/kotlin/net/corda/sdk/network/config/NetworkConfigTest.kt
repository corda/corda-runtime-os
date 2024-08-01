package net.corda.sdk.network.config

import net.corda.sdk.network.config.NetworkConfig.Companion.DUPLICATE_X500_NAMES_ERROR_MESSAGE
import net.corda.sdk.network.config.NetworkConfig.Companion.MULTIPLE_MGM_ERROR_MESSAGE
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class NetworkConfigTest {
    private val staticNetworkConfigFile = this::class.java.getResource("/config/static-network-config.json")!!.file
    private val dynamicNetworkConfigFile = this::class.java.getResource("/config/dynamic-network-config.json")!!.file

    @Test
    fun canParseStaticNetworkFile() {
        val networkConfig = NetworkConfig(configFilePath = staticNetworkConfigFile)
        assertThat(networkConfig.vNodes).isNotEmpty
    }

    @Test
    fun mgmIsNotPresentInStaticNetworkFile() {
        val networkConfig = NetworkConfig(configFilePath = staticNetworkConfigFile)
        assertThat(networkConfig.mgmNodeIsPresentInNetworkDefinition).isFalse
    }

    @Test
    fun canParseDynamicNetworkFile() {
        val networkConfig = NetworkConfig(configFilePath = dynamicNetworkConfigFile)
        assertThat(networkConfig.vNodes).isNotEmpty
    }

    @Test
    fun mgmIsPresentInDynamicNetworkFile() {
        val networkConfig = NetworkConfig(configFilePath = dynamicNetworkConfigFile)
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
        val networkConfig = NetworkConfig(configFilePath = staticNetworkConfigFile)
        val nodesWhoArentMgm = networkConfig.getVNodesWhoAreNotMgm()
        assertThat(nodesWhoArentMgm).hasSize(5)
    }

    @Test
    fun canFilterListToNonMgmDynamicNetwork() {
        val networkConfig = NetworkConfig(configFilePath = dynamicNetworkConfigFile)
        val nodesWhoArentMgm = networkConfig.getVNodesWhoAreNotMgm()
        assertThat(nodesWhoArentMgm).hasSize(5)
    }

    @Test
    fun errorIfDuplicateX500Names() {
        val duplicateName = "CN=Test, OU=Test Dept, O=R3, L=London, C=GB"
        val inputText = """
            [
              {
                "x500Name" : "$duplicateName",
                "cpi" : "One"
              },
              {
                "x500Name" : "$duplicateName",
                "cpi" : "MGM",
                "mgmNode" : "true"
              }
           ]
        """.trimIndent()
        val networkFile = kotlin.io.path.createTempFile("duplicateNamesNetworkConfig", ".json").also {
            it.toFile().deleteOnExit()
        }.toFile()
        networkFile.writeText(inputText)
        val em = assertThrows<IllegalArgumentException> {
            NetworkConfig(configFilePath = networkFile.absolutePath)
        }
        assertThat(em.message).isEqualTo(DUPLICATE_X500_NAMES_ERROR_MESSAGE)
    }
}
