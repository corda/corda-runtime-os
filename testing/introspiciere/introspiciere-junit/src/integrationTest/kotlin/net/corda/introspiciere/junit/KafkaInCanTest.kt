package net.corda.introspiciere.junit

import net.corda.data.demo.DemoRecord
import net.corda.introspiciere.payloads.Msg
import net.corda.introspiciere.payloads.MsgBatch
import net.corda.testdoubles.http.StartFakeHttpServer
import net.corda.testdoubles.http.fakeHttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.concurrent.Semaphore

@StartFakeHttpServer
internal class KafkaInCanTest {

    private lateinit var client: IntrospiciereClient

    @BeforeEach
    fun beforeEach() {
        client = IntrospiciereClient(fakeHttpServer.endpoint)
    }

    @AfterEach
    fun afterEach() {
        client.endAndjoinThreads()
    }

    @Test
    fun `kafka in a can`() {
        var counter = 0
        val seq = generateSequence {
            counter += 1
            DemoRecord(counter)
        }.iterator()

        fakeHttpServer.handle("get", "/topics/topic1/messages") {
            Assertions.assertEquals(DemoRecord::class.qualifiedName, queryParam("schema"))
            val batch = MsgBatch(
                schema = queryParam("schema")!!,
                nextBatchTimestamp = Instant.now().toEpochMilli(),
                messages = listOf(
                    Msg(
                        timestamp = Instant.now().toEpochMilli(),
                        key = null,
                        data = seq.next().toByteBuffer().toByteArray()
                    )
                )
            )
            json(batch)
        }

        val semaphore = Semaphore(-100)
        client.handle<DemoRecord>("topic1") { _, _ ->
            semaphore.release()
        }

        semaphore.acquire()
    }
}