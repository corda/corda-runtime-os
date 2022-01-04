package net.corda.messaging.kafka.utils

import net.corda.test.util.eventually
import net.corda.v5.base.util.millis
import net.corda.v5.base.util.seconds
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture

@Suppress("ExplicitGarbageCollectionCall")
class WeakValueHashMapTest {

    @Test
    fun `test WeakValueHashMap functionality and garbage collection`() {
        val map = WeakValueHashMap<String, CompletableFuture<String>>()
        var future = CompletableFuture<String>()
        val futureToRemove = CompletableFuture<String>()

        //test base functionality
        map["future"] = future
        map["remove"] = futureToRemove
        Assertions.assertEquals(future, map["future"])
        Assertions.assertTrue(map.containsKey("future"))
        map.remove("remove")
        Assertions.assertNull(map["remove"])
        Assertions.assertEquals(1, map.size)
        Assertions.assertFalse(map.isEmpty())

        //test garbage collection
        future.complete("completed")
        //new assignment so we orphan the old future
        future = CompletableFuture<String>()
        future.complete("completed yet again")
        System.gc()
        eventually(waitBetween = 10.millis, waitBefore = 0.millis, duration = 5.seconds) {
            Assertions.assertTrue(map.isEmpty())
        }

    }

    @Test
    fun `test WeakValueHashMap keys and clear`() {
        val map = WeakValueHashMap<String, CompletableFuture<String>>()
        val future = CompletableFuture<String>()
        map["future"] = future

        Assertions.assertEquals(setOf("future"), map.keys)
        map.clear()
        Assertions.assertTrue(map.isEmpty())
    }
}
