package net.corda.mgm.memberviewer

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.configuration.read.ConfigurationReadService
import net.corda.cpiinfo.read.CpiInfoReadService
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.membership.grouppolicy.GroupPolicyProvider
import net.corda.membership.impl.MemberInfoExtension.Companion.endpoints
import net.corda.membership.impl.MemberInfoExtension.Companion.identityKeyHashes
import net.corda.membership.impl.MemberInfoExtension.Companion.modifiedTime
import net.corda.membership.impl.MemberInfoExtension.Companion.status
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import net.corda.virtualnode.HoldingIdentity
import net.corda.virtualnode.read.VirtualNodeInfoReadService
import org.osgi.framework.FrameworkUtil
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.Logger
import picocli.CommandLine
import java.io.File
import java.io.FileInputStream
import java.util.*

@Component(immediate = true)
@Suppress("LongParameterList")
class MemberViewer @Activate constructor(
    @Reference(service = Shutdown::class)
    private val shutDownService: Shutdown,
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = MembershipGroupReaderProvider::class)
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider,
    @Reference(service = GroupPolicyProvider::class)
    private val groupPolicyProvider: GroupPolicyProvider,
    @Reference(service = VirtualNodeInfoReadService::class)
    private val virtualNodeInfoReadService: VirtualNodeInfoReadService,
    @Reference(service = CpiInfoReadService::class)
    private val cpiInfoReader: CpiInfoReadService
) : Application {

    private companion object {
        private val logger: Logger = contextLogger()
        const val KAFKA_BOOTSTRAP_SERVER = "bootstrap.servers"
        const val KAFKA_COMMON_BOOTSTRAP_SERVER = "messaging.kafka.common.bootstrap.servers"
    }

    @Suppress("SpreadOperator")
    override fun startup(args: Array<String>) {
        logger.info("Starting member viewer tool")

        val parameters = CliParameters()
        CommandLine(parameters).parseArgs(*args)
        if (parameters.helpRequested) {
            CommandLine.usage(CliParameters(), System.out)
            shutdownOSGiFramework()
        } else {
            val kafkaProperties = Properties()
            val kafkaPropertiesFile = parameters.kafkaConnection
            if (kafkaPropertiesFile == null) {
                logError("No file path passed for --kafka.")
                shutdown()
                return
            }
            kafkaProperties.load(FileInputStream(kafkaPropertiesFile))
            if (!kafkaProperties.containsKey(KAFKA_BOOTSTRAP_SERVER)) {
                logError("No $KAFKA_BOOTSTRAP_SERVER property found in file specified via --kafka")
                shutdown()
                return
            }

            if (parameters.memberNames.isNullOrEmpty()) {
                logError("No member name property found in file specified via --member")
                shutdown()
                return
            }

            if (parameters.groupId.isNullOrEmpty()) {
                logError("No group ID property found in file specified via --group")
                shutdown()
                return
            }
            configurationReadService.start()
            membershipGroupReaderProvider.start()
            groupPolicyProvider.start()
            virtualNodeInfoReadService.start()
            cpiInfoReader.start()


            val secretsConfig = ConfigFactory.empty()
            val bootConfig = ConfigFactory.empty()
                .withValue(
                    KAFKA_COMMON_BOOTSTRAP_SERVER,
                    ConfigValueFactory.fromAnyRef(kafkaProperties[KAFKA_BOOTSTRAP_SERVER].toString())
                )
            val smartConfig = SmartConfigFactory.create(secretsConfig).create(bootConfig)

            configurationReadService.bootstrapConfig(smartConfig)

            val members = parameters.memberNames!!.map {
                HoldingIdentity(MemberX500Name.parse(it).toString(), parameters.groupId!!)
            }
            members.forEach {
                try {
                    membershipGroupReaderProvider.getGroupReader(it).printMemberListToConsole()
                } catch (e: CordaRuntimeException) {
                    Thread.sleep(5000)
                    membershipGroupReaderProvider.getGroupReader(it).printMemberListToConsole()
                }
            }

            shutdown()
        }
    }

    fun MembershipGroupReader.printMemberListToConsole() {
        logger.info("=================")
        logger.info("View of group data for member [$owningMember] in group [$groupId]")
        lookup().forEach {
            logger.info(
"""
{
    name: ${it.name},
    status: ${it.status},
    platformVersion: ${it.platformVersion},
    endpoints: [
    ${
        it.endpoints.joinToString(",\n") { endpoint ->
    """
        {
            url: ${endpoint.url},
            protocol: ${endpoint.protocolVersion}
        }
    """.trimIndent()
        }
    }
    ],
    modifiedTime: ${it.modifiedTime},
    publicKeyHashes: [
        ${it.identityKeyHashes.joinToString(",\n") { hash -> hash.value }}
    ]
}
""".trimIndent()
            )
        }
    }

    override fun shutdown() {
        logger.info("Shutting down member list viewer tool")
        shutdownOSGiFramework()
    }

    private fun shutdownOSGiFramework() {
        shutDownService.shutdown(FrameworkUtil.getBundle(this::class.java))
    }

    private fun logError(error: String) {
        logger.error(error)
    }

}

class CliParameters {
    @CommandLine.Option(
        names = ["--kafka"], description = ["File containing Kafka connection properties."]
    )
    var kafkaConnection: File? = null

    @CommandLine.Option(
        names = ["--member"],
        description = ["Member x500 name for the members whose view on the data is being requested. Can be repeated multiple times."]
    )
    var memberNames: List<String>? = null

    @CommandLine.Option(
        names = ["--group"], description = ["The group ID."]
    )
    var groupId: String? = null

    @CommandLine.Option(names = ["-h", "--help"], usageHelp = true, description = ["Display help and exit"])
    var helpRequested = false
}