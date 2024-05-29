package net.corda.gradle.plugin

import net.corda.e2etest.utilities.DEFAULT_CLUSTER
import net.corda.restclient.CordaRestClient

object TestExecutionConditions {
    private val targetUrl = DEFAULT_CLUSTER.rest.uri
    private val user = DEFAULT_CLUSTER.rest.user
    private val password = DEFAULT_CLUSTER.rest.password

    private val cordaRestClient by lazy { CordaRestClient.createHttpClient(targetUrl, user, password, insecure = true) }
    private val isHelloEndpointReachable: Boolean by lazy {
        runCatching {
            cordaRestClient.helloRestClient.postHello("Hello, Corda!")
        }.isSuccess
    }

    fun isRestApiReachable(): Boolean = isHelloEndpointReachable
}
