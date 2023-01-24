package net.corda.httprpc.server.impl.rpcops.impl

import net.corda.httprpc.server.impl.rpcops.TestPingPongAPI
import net.corda.httprpc.PluggableRestResource

class TestPingPongAPIImpl : TestPingPongAPI, PluggableRestResource<TestPingPongAPI> {

    override val targetInterface: Class<TestPingPongAPI>
        get() = TestPingPongAPI::class.java

    override val protocolVersion: Int
        get() = 1

    override fun ping(data: TestPingPongAPI.PingPongData?) = "Ping Pong"
}