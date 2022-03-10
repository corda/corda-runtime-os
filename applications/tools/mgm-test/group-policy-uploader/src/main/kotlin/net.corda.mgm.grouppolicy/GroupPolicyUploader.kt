package net.corda.mgm.grouppolicy

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.libs.configuration.SmartConfig
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.libs.packaging.CpiIdentifier
import net.corda.libs.packaging.CpiMetadata
import net.corda.libs.packaging.CpkMetadata
import net.corda.messaging.api.publisher.config.PublisherConfig
import net.corda.messaging.api.publisher.factory.PublisherFactory
import net.corda.messaging.api.records.Record
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import net.corda.schema.Schemas
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import net.corda.v5.crypto.SecureHash
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.VirtualNodeInfo
import net.corda.virtualnode.toAvro
import org.osgi.framework.FrameworkUtil
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import picocli.CommandLine
import java.io.File
import java.io.FileInputStream
import java.util.*

@Suppress("UNUSED")
@Component(immediate = true)
class GroupPolicyUploader @Activate constructor(
    @Reference(service = Shutdown::class)
    private val shutDownService: Shutdown,
    @Reference(service = PublisherFactory::class)
    private val publisherFactory: PublisherFactory,
) : Application {
    companion object {
        val logger = contextLogger()
        const val GROUP_ID = "groupId"
        const val KAFKA_BOOTSTRAP_SERVER = "bootstrap.servers"
        const val CLIENT_ID = "group-policy-publisher"
        const val KAFKA_COMMON_BOOTSTRAP_SERVER = "messaging.kafka.common.bootstrap.servers"
        const val PRODUCER_CLIENT_ID = "messaging.kafka.producer.client.id"
    }

    @Suppress("SpreadOperator")
    override fun startup(args: Array<String>) {
        val parameters = CliParameters()
        CommandLine(parameters).parseArgs(*args)
        if (parameters.helpRequested) {
            CommandLine.usage(CliParameters(), System.out)
            shutdown()
            return
        }

        val kafkaProperties = Properties()
        val kafkaPropertiesFile = parameters.kafkaConnection
        if (kafkaPropertiesFile == null) {
            logError("No file path passed for --kafka.")
            shutdown()
            return
        }
        kafkaProperties.load(FileInputStream(kafkaPropertiesFile))
        if (!kafkaProperties.containsKey(KAFKA_BOOTSTRAP_SERVER)) {
            logError("No $KAFKA_BOOTSTRAP_SERVER property found in file specified via --kafka!")
            shutdown()
            return
        }

        if (parameters.policyFile == null) {
            logError("No value passed for --policy-file.")
            shutdown()
            return
        }
        val groupPolicy = parameters.policyFile!!.readText(Charsets.UTF_8)

        if (parameters.memberNames.isNullOrEmpty()) {
            logError("No value passed for --member. Expected to be one or more usages of this flag.")
            shutdown()
            return
        }
        val memberX500Names = parameters.memberNames!!.map { MemberX500Name.parse(it) }

        val groupId = getGroupId(groupPolicy)

        // Create a dummy CPI info linked to the parameter group policy.
        val cpiIdentifier = createCpiIdentifier()
        val cpiMetadata = createCpiMetadata(cpiIdentifier, groupPolicy)

        // Link each parameter member with the group ID in the group policy and the CPI info created previously.
        val vnodeInfos = memberX500Names.map {
            VirtualNodeInfo(
                HoldingIdentity(it.toString(), groupId),
                cpiMetadata.cpiId,
                vaultDmlConnectionId = UUID.randomUUID(),
                cryptoDmlConnectionId = UUID.randomUUID()
            )
        }

        with(
            publisherFactory.createPublisher(
                PublisherConfig(CLIENT_ID),
                createKafkaConfig(kafkaProperties)
            )
        ) {
            start()
            publish(cpiMetadata.toKafkaRecords())
            publish(vnodeInfos.toKafkaRecords())
            close()
        }
        shutdown()
    }

    override fun shutdown() {
        shutdownOSGiFramework()
        logger.info("Shutting down group policy uploader tool")
    }

    private fun getGroupId(groupPolicy: String): String {
        // Parse to map (taken from GroupPolicyParser class)
        val groupPolicyMap = ConfigFactory
            .parseString(groupPolicy)
            .root()
            .unwrapped()

        return groupPolicyMap[GROUP_ID]!! as String
    }

    private fun createKafkaConfig(kafkaProperties: Properties): SmartConfig {
        val bootConf = ConfigFactory.empty()
            .withValue(
                KAFKA_COMMON_BOOTSTRAP_SERVER,
                ConfigValueFactory.fromAnyRef(kafkaProperties[KAFKA_BOOTSTRAP_SERVER].toString())
            )
            .withValue(PRODUCER_CLIENT_ID, ConfigValueFactory.fromAnyRef(CLIENT_ID))
        return SmartConfigFactory.create(bootConf).create(bootConf)
    }

    private fun createCpiIdentifier(
        name: String = "MGM_TEST",
        version: String = "1.0"
    ) = CpiIdentifier(name, version, null)

    private fun createCpiMetadata(
        cpiIdentifier: CpiIdentifier,
        groupPolicy: String,
        hash: SecureHash = SecureHash.create("SHA-256:0000000000000000"),
        cpks: Collection<CpkMetadata> = emptyList()
    ) = CpiMetadata(
        cpiIdentifier,
        hash,
        cpks,
        groupPolicy
    )

    private fun List<VirtualNodeInfo>.toKafkaRecords() =
        map {
            Record(
                Schemas.VirtualNode.VIRTUAL_NODE_INFO_TOPIC,
                it.holdingIdentity.toAvro(),
                it.toAvro()
            )
        }

    private fun CpiMetadata.toKafkaRecords() =
        listOf(
            Record(
                Schemas.VirtualNode.CPI_INFO_TOPIC,
                cpiId.toAvro(),
                toAvro()
            )
        )

    private fun shutdownOSGiFramework() {
        shutDownService.shutdown(FrameworkUtil.getBundle(this::class.java))
    }

    private fun logError(error: String) {
        logger.error(error)
    }
}

class CliParameters {
    @CommandLine.Option(names = ["--kafka"], description = ["File containing Kafka connection properties."])
    var kafkaConnection: File? = null

    @CommandLine.Option(names = ["--policy-file"], description = ["GroupPolicy.json file to push to kafka"])
    var policyFile: File? = null

    @CommandLine.Option(
        names = ["--member"],
        description = ["Member x500 name for which this group policy should be added. Can be repeated multiple times."]
    )
    var memberNames: List<String>? = null

    @CommandLine.Option(names = ["-h", "--help"], usageHelp = true, description = ["Display help and exit"])
    var helpRequested = false
}