package net.corda.httprpc.server.apigen.test

import net.corda.v5.httprpc.api.PluggableRPCOps

class NonCordaSerializableAPIImpl : NonCordaSerializableAPI, PluggableRPCOps<NonCordaSerializableAPI> {
    override val targetInterface: Class<NonCordaSerializableAPI>
        get() = NonCordaSerializableAPI::class.java

    override val protocolVersion: Int
        get() = 2

    override fun call(data: NonCordaSerializableClass): String = "OK"
}