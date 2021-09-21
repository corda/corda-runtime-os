package net.corda.httprpc.server.impl.apigen.test

import net.corda.v5.httprpc.api.PluggableRPCOps

class TestPingPongAPIImpl : TestPingPongAPI, PluggableRPCOps<TestPingPongAPI> {

    override val targetInterface: Class<TestPingPongAPI>
        get() = TestPingPongAPI::class.java

    override val protocolVersion: Int
        get() = 1

    override fun ping(data: TestPingPongAPI.PingPongData?) = "Ping Pong"
}