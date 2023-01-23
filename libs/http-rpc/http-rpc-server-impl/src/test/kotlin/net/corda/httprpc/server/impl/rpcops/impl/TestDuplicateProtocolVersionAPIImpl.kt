package net.corda.httprpc.server.impl.rpcops.impl

import net.corda.httprpc.server.impl.rpcops.TestDuplicateProtocolVersionAPI
import net.corda.httprpc.PluggableRestResource

internal class TestDuplicateProtocolVersionAPIImpl : TestDuplicateProtocolVersionAPI,
    PluggableRestResource<TestDuplicateProtocolVersionAPI> {

    override val targetInterface: Class<TestDuplicateProtocolVersionAPI>
        get() = TestDuplicateProtocolVersionAPI::class.java

    override val protocolVersion: Int
        get() = 2

    override fun test(): String = "OK"
}