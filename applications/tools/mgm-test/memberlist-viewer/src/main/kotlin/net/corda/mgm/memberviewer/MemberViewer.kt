package net.corda.mgm.memberviewer

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import net.corda.configuration.read.ConfigurationReadService
import net.corda.libs.configuration.SmartConfigFactory
import net.corda.membership.impl.MemberInfoExtension.Companion.endpoints
import net.corda.membership.impl.MemberInfoExtension.Companion.identityKeyHashes
import net.corda.membership.impl.MemberInfoExtension.Companion.modifiedTime
import net.corda.membership.impl.MemberInfoExtension.Companion.status
import net.corda.membership.read.MembershipGroupReader
import net.corda.membership.read.MembershipGroupReaderProvider
import net.corda.osgi.api.Application
import net.corda.osgi.api.Shutdown
import net.corda.schema.configuration.MessagingConfig
import net.corda.schema.configuration.MessagingConfig.Bus.BOOTSTRAP_SERVER
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.base.util.contextLogger
import net.corda.v5.membership.EndpointInfo
import net.corda.v5.membership.MemberInfo
import net.corda.virtualnode.HoldingIdentity
import org.osgi.framework.BundleContext
import org.osgi.framework.FrameworkUtil
import org.osgi.framework.ServiceReference
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
    @Reference(service = ConfigurationReadService::class)
    private val configurationReadService: ConfigurationReadService,
    @Reference(service = MembershipGroupReaderProvider::class)
    private val membershipGroupReaderProvider: MembershipGroupReaderProvider
) : Application {

    private companion object {
        private val logger: Logger = contextLogger()
        const val KAFKA_BOOTSTRAP_SERVER = "bootstrap.servers"
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

            val secretsConfig = ConfigFactory.empty()
            val bootConfig = ConfigFactory.empty()
                .withValue(
                    BOOTSTRAP_SERVER,
                    ConfigValueFactory.fromAnyRef(kafkaProperties[KAFKA_BOOTSTRAP_SERVER].toString())
                )
                .withValue(
                    MessagingConfig.Bus.BUS_TYPE,
                    ConfigValueFactory.fromAnyRef("KAFKA")
                )
            val smartConfig = SmartConfigFactory.create(secretsConfig).create(bootConfig)

            configurationReadService.bootstrapConfig(smartConfig)

            val members = parameters.memberNames!!.map {
                HoldingIdentity(MemberX500Name.parse(it).toString(), parameters.groupId!!)
            }
            // Sleep to allow group reader provider time to get config and start.
            // Not ideal solution but just is just a temporary test tool so it will do.
            Thread.sleep(3000)

            members.forEach {
                membershipGroupReaderProvider.getGroupReader(it).printMemberListToConsole()
            }

            shutdown()
        }
    }

    private fun MemberInfo.readableString(): String {
        return """{
            name: ${name},
            status: ${status},
            platformVersion: ${platformVersion},
            endpoints: [
                ${endpoints.joinToString(",\n") { endpoint -> endpoint.readableString() }}
            ],
            modifiedTime: ${modifiedTime},
            identityKeyHashes: [
                ${identityKeyHashes.joinToString(",\n") { hash -> hash.value }}
            ]
        }"""
    }

    private fun EndpointInfo.readableString(): String {
        return """{
                    url: $url,
                    protocol: $protocolVersion
                }"""
    }

    private fun MembershipGroupReader.printMemberListToConsole() {
        logger.info("=================")
        logger.info("View of group data for member [$owningMember] in group [$groupId]")
        logger.info(
            """
{
    members: [
        ${lookup().joinToString(",\n") { it.readableString() }}
    ]
}
        """
        )
        logger.info("=================")
    }


    override fun shutdown() {
        logger.info("Shutting down member list viewer tool")
        shutdownOSGiFramework()
    }

    private fun shutdownOSGiFramework() {
        val bundleContext: BundleContext? = FrameworkUtil.getBundle(MemberViewer::class.java).bundleContext
        if (bundleContext != null) {
            val shutdownServiceReference: ServiceReference<Shutdown>? =
                bundleContext.getServiceReference(Shutdown::class.java)
            if (shutdownServiceReference != null) {
                bundleContext.getService(shutdownServiceReference)?.shutdown(bundleContext.bundle)
            }
        }
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