package net.corda.messaging.mediator

import net.corda.messaging.api.mediator.MediatorMessage
import net.corda.messaging.mediator.mocks.MockRPCClient
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MockRPCClientTest {
    private lateinit var client: MockRPCClient
    private val clientId = "TestClient"
    private val delayTime = 100L // 100ms
    private val mockMessage = MediatorMessage("Test".toByteArray(Charsets.UTF_8))
    private val valueToReturn = MediatorMessage("$clientId-test".toByteArray(Charsets.UTF_8))

    @BeforeEach
    fun setUp() {
        client = MockRPCClient(clientId, delayTime, valueToReturn)
    }

    @Test
    fun `send returns predefined value`() {
        val response = client.send(mockMessage)
        assertEquals(valueToReturn, response)
    }

    @Test
    fun `send introduces correct delay`() {
        val latch = CountDownLatch(1)
        val delayTime = 100L // 100ms

        Thread {
            client.send(mockMessage)
            latch.countDown()
        }.start()

        // Add 50ms buffer to be safe
        val completed = latch.await(delayTime + 50, TimeUnit.MILLISECONDS)
        assertTrue(completed)
    }

    @Test
    fun `close method executes without exception`() {
        assertDoesNotThrow {
            client.close()
        }
    }
}