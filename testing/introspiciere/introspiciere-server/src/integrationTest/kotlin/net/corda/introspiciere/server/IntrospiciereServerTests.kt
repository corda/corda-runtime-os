package net.corda.introspiciere.server

import io.javalin.testtools.TestUtil
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.BindException
import java.net.ServerSocket

class IntrospiciereServerTests {
    @Test
    fun `Server binds to the specified port`() {
        val port = findUnusedPort()
        IntrospiciereServer(port).use {
            it.start()
            assertEquals(port, it.portUsed, "Listening port")
            assertTrue(isPortUsed(port), "Listening port not in use")
        }
    }

    @Test
    fun `Server binds to next available port`() {
        ServerSocket(7070).use {
            IntrospiciereServer().use {
                it.start()
                assertNotEquals(7070, it.portUsed, "Listening port not 7070")
                assertTrue(isPortUsed(it.portUsed), "Listening port not in use")
            }
        }
    }

    private fun findUnusedPort(): Int {
        return ServerSocket(0).use {
            it.localPort
        }
    }

    private fun isPortUsed(port: Int): Boolean {
        return try {
            ServerSocket(port).close()
            false
        } catch (ex: BindException) {
            true
        }
    }
}

class A {
    @Test
    fun a() {
        TestUtil.test(IntrospiciereServer().app) { _, client ->
            val greeting = client.get("/helloworld").body!!.string()
            assertEquals("Hello world!!", greeting)
        }
    }

    @Test
    fun b() {
        IntrospiciereServer().use {
            it.start()
        }
    }

    @Test
    fun c() {
        TestUtil.test(IntrospiciereServer().app) { _, client ->
            val greeting = client.get("/helloworld").body!!.string()
            assertEquals("Hello world!!", greeting)
        }
    }
}