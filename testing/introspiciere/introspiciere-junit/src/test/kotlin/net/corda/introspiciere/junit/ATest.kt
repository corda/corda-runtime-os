package net.corda.introspiciere.junit

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

internal class ATest {
    @RegisterExtension
    val hostA = DeployCluster("alpha")

    @Test
    fun first() {
        println(hostA.helloWorld())
        println(hostA.fetchTopics())
        hostA.createKeyAndAddIdentity("alice", "ECDSA")
    }
}