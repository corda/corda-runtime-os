package net.corda.messaging.api.util

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
        Thread.sleep(1000)
        Assertions.assertTrue(map.isEmpty())

    }
}