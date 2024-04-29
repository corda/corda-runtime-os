package net.corda.messaging.mediator.processor

import java.util.concurrent.Semaphore

class QuotaAction(val quota: Int) {
    private val semaphore = Semaphore(quota, true)

    fun <T, C : Collection<T>> withQuota(task: () -> C): C {
        val collection = task()
        if (collection.size > quota) throw IllegalStateException("Returned collection for withQuota should always be sized less than the overall quota")
        semaphore.acquire(collection.size)
        return collection
    }

    fun complete(quantity: Int) {
        semaphore.release(quantity)
    }

    fun useQuota(quantity: Int) {
        if (quantity > quota) throw IllegalStateException("Returned useQuota should always be sized less than the overall quota")
        semaphore.acquire(quantity)
    }
}