package net.corda.p2p.setup

import com.typesafe.config.Config
import com.typesafe.config.ConfigException.Missing
import com.typesafe.config.ConfigFactory
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.COMPONENT_NAME
import net.corda.libs.configuration.schema.p2p.LinkManagerConfiguration.Companion.PACKAGE_NAME
import net.corda.messaging.api.records.Record
import net.corda.p2p.setup.AddGroup.Companion.toGroupRecord
import net.corda.p2p.setup.AddIdentity.Companion.toIdentityRecord
import net.corda.p2p.setup.AddKeyPair.Companion.toKeysRecord
import net.corda.p2p.setup.AddMember.Companion.toMemberRecord
import net.corda.schema.TestSchema
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters
import java.io.File
import java.util.concurrent.Callable

@Command(
    name = "apply",
    description = ["Apply setup from a file"],
    mixinStandardHelpOptions = true,
    showDefaultValues = true,
)

class Apply : Callable<Collection<Record<String, *>>> {
    @Parameters(
        description = [
            "A file with the details to apply."
        ]
    )
    internal lateinit var dataFile: File

    private fun recordsFromConfigurations(
        data: Config,
        name: String,
        handler: (Config) -> Record<String, *>
    ): Collection<Record<String, *>> {
        return try {
            data.getConfigList(name).map {
                handler.invoke(it)
            }
        } catch (_: Missing) {
            emptyList()
        }
    }

    private fun applyOnConfiguration(data: Config): Collection<Record<String, *>> {
        val gatewayConfiguration = try {
            listOf(
                data
                    .getConfig("gatewayConfig")
                    .toConfigurationRecord("p2p", "record")
            )
        } catch (_: Missing) {
            emptyList()
        }

        val linkManagerConfiguration = try {
            listOf(
                data
                    .getConfig("linkManagerConfig")
                    .toConfigurationRecord(
                        PACKAGE_NAME,
                        COMPONENT_NAME,
                    )
            )
        } catch (_: Missing) {
            emptyList()
        }

        val groupsToRemove = try {
            data.getStringList("groupsToRemove").map {
                Record(
                    TestSchema.GROUP_POLICIES_TOPIC,
                    it,
                    null
                )
            }
        } catch (_: Missing) {
            emptyList()
        }

        return gatewayConfiguration + linkManagerConfiguration + recordsFromConfigurations(
            data,
            "groupsToAdd",
        ) {
            it.toGroupRecord()
        } + recordsFromConfigurations(
            data,
            "identitiesToAdd",
        ) {
            it.toIdentityRecord()
        } + recordsFromConfigurations(
            data,
            "membersToAdd",
        ) {
            it.toMemberRecord()
        } + recordsFromConfigurations(
            data,
            "keysToAdd",
        ) {
            it.toKeysRecord()
        } + recordsFromConfigurations(
            data,
            "membersToRemove"
        ) {
            val groupId = it.getString("groupId")
            val x500Name = it.getString("x500name")
            Record(
                TestSchema.MEMBER_INFO_TOPIC,
                "$x500Name-$groupId",
                null
            )
        } + recordsFromConfigurations(
            data,
            "identitiesToRemove"
        ) {
            val groupId = it.getString("groupId")
            val x500Name = it.getString("x500name")
            Record(
                TestSchema.HOSTED_MAP_TOPIC,
                "$x500Name-$groupId",
                null
            )
        } + groupsToRemove
    }

    override fun call(): Collection<Record<String, *>> {
        if (!dataFile.canRead()) {
            throw SetupException("Can not read data from $dataFile.")
        }
        val data = ConfigFactory.parseFile(dataFile)
        return applyOnConfiguration(data)
    }
}
