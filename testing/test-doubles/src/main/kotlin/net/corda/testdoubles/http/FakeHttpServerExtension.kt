package net.corda.testdoubles.http

import net.corda.testdoubles.GlobalVariableNotInitialised
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

val fakeHttpServer: FakeHttpServer
    get() = internalFakeHttpServer ?: throw GlobalVariableNotInitialised(::fakeHttpServer)

private var internalFakeHttpServer: FakeHttpServer? = null

class FakeHttpServerExtension(val server: FakeHttpServer = FakeHttpServer()) : BeforeEachCallback, AfterEachCallback {
    override fun beforeEach(context: ExtensionContext?) {
        server.start()
        internalFakeHttpServer = server
    }

    override fun afterEach(context: ExtensionContext?) {
        server.stop()
        internalFakeHttpServer = null
    }
}

