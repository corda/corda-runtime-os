package net.corda.introspiciere.junit

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

internal class ATest {
    @RegisterExtension
    val hostA = DeployCluster("host-a")

    @Test
    fun first() {
        println(hostA.helloworld())
    }
}

