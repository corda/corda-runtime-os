package net.corda.applications.workers.rest.utils

import net.corda.applications.workers.rest.http.TestToolkitProperty
import net.corda.applications.workers.rest.kafka.KafkaTestToolKit
import net.corda.rest.RestResource
import net.corda.rest.client.RestClient

interface E2eCluster {
    val members: List<E2eClusterMember>

    val clusterConfig: E2eClusterConfig

    val kafkaTestToolkit: KafkaTestToolKit

    val p2pUrl: String

    fun addMember(memberToAdd: E2eClusterMember)

    fun addMembers(membersToAdd: List<E2eClusterMember>)

    val uniqueName: String

    /**
     * Creates the [RestClient] for a given [RestResource] class.
     */
    fun <I : RestResource> clusterHttpClientFor(
        restResourceClass: Class<I>,
        userName: String = AdminPasswordUtil.adminUser
    ): RestClient<I>
}

internal object E2eClusterFactory {
    fun getE2eCluster(clusterConfig: E2eClusterConfig = E2eClusterAConfig): E2eCluster {
        return E2eClusterImpl(clusterConfig)
    }
}

private class E2eClusterImpl(
    override val clusterConfig: E2eClusterConfig
) : E2eCluster {
    override val members = mutableListOf<E2eClusterMember>()

    private val testToolkit by TestToolkitProperty(
        clusterConfig.restHost,
        clusterConfig.restPort
    )

    override val p2pUrl get() = "https://${clusterConfig.p2pHost}:${clusterConfig.p2pPort}"

    override val kafkaTestToolkit: KafkaTestToolKit by lazy {
        KafkaTestToolKit(testToolkit)
    }

    override fun addMember(memberToAdd: E2eClusterMember) {
        members.add(memberToAdd)
    }

    override fun addMembers(membersToAdd: List<E2eClusterMember>) {
        members.addAll(membersToAdd)
    }

    override val uniqueName: String
        get() = testToolkit.uniqueName

    override fun <I : RestResource> clusterHttpClientFor(
        restResourceClass: Class<I>,
        userName: String
    ): RestClient<I> = testToolkit.httpClientFor(restResourceClass, userName, clusterConfig.restPassword)
}