package net.corda.httprpc.server.apigen.test

import net.corda.v5.httprpc.api.PluggableRPCOps

class TestRPCAPIImpl : TestRPCAPI, PluggableRPCOps<TestRPCAPI> {

    override val targetInterface: Class<TestRPCAPI>
        get() = TestRPCAPI::class.java

    override val protocolVersion: Int
        get() = 2

    override fun void() = "Sane"
}

class TestRPCAPIAnnotatedImpl : TestRPCAPIAnnotated, PluggableRPCOps<TestRPCAPIAnnotated> {
    override val targetInterface: Class<TestRPCAPIAnnotated>
        get() = TestRPCAPIAnnotated::class.java

    override val protocolVersion: Int
        get() = 2

    override fun void() = "Sane"
}