package net.corda.httprpc.server.impl.rpcops.impl

import net.corda.httprpc.server.impl.rpcops.NonCordaSerializableAPI
import net.corda.httprpc.server.impl.rpcops.NonCordaSerializableClass
import net.corda.httprpc.PluggableRestResource

class NonCordaSerializableAPIImpl : NonCordaSerializableAPI, PluggableRestResource<NonCordaSerializableAPI> {
    override val targetInterface: Class<NonCordaSerializableAPI>
        get() = NonCordaSerializableAPI::class.java

    override val protocolVersion: Int
        get() = 2

    override fun call(data: NonCordaSerializableClass): String = "OK"
}