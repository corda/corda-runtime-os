package net.corda.introspiciere.server

import io.restassured.RestAssured
import net.corda.introspiciere.server.fakes.FakeAppContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

abstract class IntrospiciereServerWithFakeContext {
    protected lateinit var fakeAppContext: FakeAppContext
    private lateinit var server: IntrospiciereServer

    @BeforeEach
    fun beforeEach() {
        fakeAppContext = FakeAppContext()
        server = IntrospiciereServer(fakeAppContext)
        server.start()
        RestAssured.port = server.portUsed
    }

    @AfterEach
    fun afterEach() {
        server.close()
    }
}