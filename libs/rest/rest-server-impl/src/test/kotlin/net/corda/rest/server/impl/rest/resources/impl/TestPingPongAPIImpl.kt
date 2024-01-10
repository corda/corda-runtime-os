package net.corda.rest.server.impl.rest.resources.impl

import net.corda.rest.server.impl.rest.resources.TestPingPongAPI
import net.corda.rest.PluggableRestResource

class TestPingPongAPIImpl : TestPingPongAPI, PluggableRestResource<TestPingPongAPI> {

    override val targetInterface: Class<TestPingPongAPI>
        get() = TestPingPongAPI::class.java

    override val protocolVersion: Int
        get() = 1

    override fun ping(data: TestPingPongAPI.PingPongData?) = "Ping Pong"
}