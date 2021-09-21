package net.corda.httprpc.server.impl.apigen.test

import net.corda.v5.httprpc.api.PluggableRPCOps

internal class TestDuplicateProtocolVersionAPIImpl : TestDuplicateProtocolVersionAPI, PluggableRPCOps<TestDuplicateProtocolVersionAPI> {

    override val targetInterface: Class<TestDuplicateProtocolVersionAPI>
        get() = TestDuplicateProtocolVersionAPI::class.java

    override val protocolVersion: Int
        get() = 2

    override fun test(): String = "OK"
}