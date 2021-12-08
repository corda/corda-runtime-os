package net.corda.p2p.deployment.pods

import net.corda.p2p.deployment.CordaOsDockerDevSecret
import net.corda.p2p.deployment.commands.MyUserName
import java.io.File

data class NamespaceIdentifier(
    val namespaceName: String,
    val x500Name: String,
    val groupId: String,
    val hostName: String,
)
data class P2PDeploymentDetails(
    val linkManagerCount: Int,
    val gatewayCount: Int,
    val debug: Boolean,
    val tag: String,
    val resourceRequest: ResourceRequest,
)
data class DbDetails(
    val username: String,
    val password: String,
    val sqlInitFile: File?
)
data class InfrastructureDetails(
    val kafkaBrokerCount: Int,
    val zooKeeperCount: Int,
    val disableKafkaUi: Boolean,
    val dbDetails: DbDetails,
    val kafkaBrokerResourceRequest: ResourceRequest,
)

class Namespace(
    identifier: NamespaceIdentifier,
    p2pDeployment: P2PDeploymentDetails,
    infrastructureDetails: InfrastructureDetails,
) {

    val nameSpaceYaml by lazy {
        listOf(
            mapOf(
                "apiVersion" to "v1",
                "kind" to "Namespace",
                "metadata" to mapOf(
                    "name" to identifier.namespaceName,
                    "labels" to mapOf(
                        "namespace-type" to "p2p-deployment",
                        "creator" to MyUserName.userName,
                    ),
                    "annotations" to mapOf(
                        "type" to "p2p",
                        "x500-name" to (identifier.x500Name),
                        "group-id" to identifier.groupId,
                        "host" to identifier.hostName,
                        "debug" to p2pDeployment.debug.toString(),
                        "tag" to p2pDeployment.tag,
                        "kafkaServers" to kafkaServers
                    )
                )
            ),
            CordaOsDockerDevSecret.secret(identifier.namespaceName),
        )
    }

    private val kafkaServers by lazy {
        KafkaBroker.kafkaServers(identifier.namespaceName, infrastructureDetails.kafkaBrokerCount)
    }

    val infrastructurePods by lazy {
        KafkaBroker.kafka(
            identifier.namespaceName,
            infrastructureDetails.zooKeeperCount,
            infrastructureDetails.kafkaBrokerCount,
            !infrastructureDetails.disableKafkaUi,
            infrastructureDetails.kafkaBrokerResourceRequest,
        ) +
            PostGreSql(infrastructureDetails.dbDetails)
    }

    val p2pPods by lazy {
        Gateway.gateways(
            listOf(identifier.hostName),
            kafkaServers,
            p2pDeployment,
        ) +
            LinkManager.linkManagers(
                kafkaServers, p2pDeployment
            )
    }
}
