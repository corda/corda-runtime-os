package net.corda.messaging.emulation.rpc


import net.corda.messaging.api.exception.CordaRestAPIResponderException
import net.corda.messaging.api.processor.RPCResponderProcessor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class RPCTopicServiceImplTest {

    private val successListener0 = object : RPCResponderProcessor<Int, String> {
        override fun onNext(request: Int, respFuture: CompletableFuture<String>) {
            respFuture.complete(request.toString())
        }
    }

    private val successListener1 = object : RPCResponderProcessor<Int, String> {
        override fun onNext(request: Int, respFuture: CompletableFuture<String>) {
            respFuture.complete((request + 10).toString())
        }
    }

    private val cancelListener = object : RPCResponderProcessor<Int, String> {
        override fun onNext(request: Int, respFuture: CompletableFuture<String>) {
            respFuture.cancel(true)
        }
    }

    private val errorListener = object : RPCResponderProcessor<Int, String> {
        override fun onNext(request: Int, respFuture: CompletableFuture<String>) {
            respFuture.completeExceptionally(Exception())
        }
    }

    private val faultListener = object : RPCResponderProcessor<Int, String> {
        override fun onNext(request: Int, respFuture: CompletableFuture<String>) {
            throw CordaRestAPIResponderException("errorType", "fail")
        }
    }

    /**
     * When creating a request for a topic that has no listener/handler, no response will be received,
     * the client is expected to handle a timeout in this case.
     */
    @Test
    fun `publish to a topic without a listener does nothing`() {
        val executorService = Executors.newCachedThreadPool()
        val service = RPCTopicServiceImpl(executorService)

        val requestCompletion = CompletableFuture<String>()
        val request = 1
        val topic = "topic1"

        service.publish(topic, request, requestCompletion)

        assertThrows<TimeoutException> { requestCompletion.get(250, TimeUnit.MILLISECONDS)}
    }

    @Test
    @Timeout(1)
    fun `requests succeed when listeners handles requests`() {
        val executorService = Executors.newSingleThreadExecutor()
        val service = RPCTopicServiceImpl(executorService)

        val requestCompletion = CompletableFuture<String>()
        val request = 1
        val topic = "topic1"

        service.subscribe(topic, successListener0)
        service.publish(topic, request, requestCompletion)
        val result = requestCompletion.get()
        assertThat(result).isEqualTo("1")
    }

    @Test
    @Timeout(1)
    fun `requests failed when listeners cancel`() {
        val executorService = Executors.newSingleThreadExecutor()
        val service = RPCTopicServiceImpl(executorService)

        val requestCompletion = CompletableFuture<String>()
        val request = 1
        val topic = "topic1"

        service.subscribe(topic, cancelListener)
        service.publish(topic, request, requestCompletion)

        val exception = assertThrows<ExecutionException> { requestCompletion.get()}
        assertThat(exception.cause!!.message).isEqualTo("The request was cancelled by the responder.")
    }

    @Test
    @Timeout(1)
    fun `requests failed when listeners complete with exception`() {
        val executorService = Executors.newSingleThreadExecutor()
        val service = RPCTopicServiceImpl(executorService)

        val requestCompletion = CompletableFuture<String>()
        val request = 1
        val topic = "topic1"

        service.subscribe(topic, errorListener)
        service.publish(topic, request, requestCompletion)

        val exception = assertThrows<ExecutionException> { requestCompletion.get()}
        assertThat(exception.cause!!.message).isEqualTo("The responder failed to process the request.")
    }

    @Test
    @Timeout(1)
    fun `requests failed when listeners throw an exception`() {
        val executorService = Executors.newSingleThreadExecutor()
        val service = RPCTopicServiceImpl(executorService)

        val requestCompletion = CompletableFuture<String>()
        val request = 1
        val topic = "topic1"

        service.subscribe(topic, faultListener)
        service.publish(topic, request, requestCompletion)

        val exception = assertThrows<ExecutionException> { requestCompletion.get()}
        assertThat(exception.cause!!.message).isEqualTo("The responder failed to process the request.")
    }

    @Test
    @Timeout(1)
    fun `requests on topics with multiple listeners are distributed on round-robin basis`() {
        val executorService = Executors.newSingleThreadExecutor()
        val service = RPCTopicServiceImpl(executorService)

        val requestCompletion1 = CompletableFuture<String>()
        val requestCompletion2 = CompletableFuture<String>()
        val requestCompletion3 = CompletableFuture<String>()
        val requestCompletion4 = CompletableFuture<String>()
        val request1 = 1
        val request2 = 2
        val request3 = 3
        val request4 = 4
        val topic = "topic1"

        service.subscribe(topic, successListener0)
        service.subscribe(topic, successListener1)

        service.publish(topic, request1, requestCompletion1)
        service.publish(topic, request2, requestCompletion2)
        service.publish(topic, request3, requestCompletion3)
        service.publish(topic, request4, requestCompletion4)

        val result1 = requestCompletion1.get()
        val result2 = requestCompletion2.get()
        val result3 = requestCompletion3.get()
        val result4 = requestCompletion4.get()

        assertThat(result1).isEqualTo("1")
        assertThat(result2).isEqualTo("12")
        assertThat(result3).isEqualTo("3")
        assertThat(result4).isEqualTo("14")
    }

    @Test
    @Timeout(1)
    fun `when listener removed from a topic work distributed to remaining`() {
        val executorService = Executors.newSingleThreadExecutor()
        val service = RPCTopicServiceImpl(executorService)

        val requestCompletion1 = CompletableFuture<String>()
        val requestCompletion2 = CompletableFuture<String>()
        val request1 = 1
        val request2 = 2
        val topic = "topic1"

        service.subscribe(topic, successListener0)
        service.subscribe(topic, successListener1)

        service.publish(topic, request1, requestCompletion1)
        service.publish(topic, request2, requestCompletion2)

        val result1 = requestCompletion1.get()
        val result2 = requestCompletion2.get()

        assertThat(result1).isEqualTo("1")
        assertThat(result2).isEqualTo("12")

        service.unsubscribe(topic, successListener0)

        val requestCompletion3 = CompletableFuture<String>()
        val request3 = 3
        service.publish(topic, request3, requestCompletion3)
        val result3 = requestCompletion3.get()
        assertThat(result3).isEqualTo("13")

        service.unsubscribe(topic, successListener1)

        val requestCompletion4 = CompletableFuture<String>()
        val request4 = 4
        service.publish(topic, request4, requestCompletion4)

        // We expect a timeout now as no listeners are left to consume the request
        assertThrows<TimeoutException> { requestCompletion4.get(250, TimeUnit.MILLISECONDS)}
    }
}
