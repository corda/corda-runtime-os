package net.corda.p2p.config.publisher

import com.typesafe.config.ConfigFactory
import net.corda.libs.configuration.write.CordaConfigurationKey
import net.corda.libs.configuration.write.CordaConfigurationVersion
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters
import java.io.File

@Command(
    name = "file",
    description = ["Publish configuration from raw file"]
)
class FileConfiguration : ConfigProducer() {
    enum class Type {
        Gateway,
        LinkManager
    }
    @Parameters(description = ["The type of configuration (gateway or linkmanager)"])
    lateinit var type: Type

    @Parameters(description = ["The file with the raw configuration"])
    lateinit var file: File

    override val configuration by lazy {
        ConfigFactory.parseFile(file)
    }

    override val key by lazy {
        val name = when (type) {
            Type.Gateway -> "gateway"
            Type.LinkManager -> "link-manager"
        }
        CordaConfigurationKey(
            "p2p-link-manager",
            CordaConfigurationVersion("p2p", 1, 0),
            CordaConfigurationVersion(name, 1, 0)
        )
    }
}
