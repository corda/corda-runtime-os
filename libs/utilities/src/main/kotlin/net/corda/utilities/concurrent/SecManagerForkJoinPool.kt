package net.corda.utilities.concurrent

import java.util.concurrent.ForkJoinPool
import java.util.concurrent.ForkJoinPool.ForkJoinWorkerThreadFactory
import java.util.concurrent.ForkJoinWorkerThread

/**
 * [ForkJoinPool] that doesn't change permissions of threads when [SecurityManager] is enabled.
 */
class SecManagerForkJoinPool {
    companion object {
        val pool =  ForkJoinPool(
            Runtime.getRuntime().availableProcessors(),
            ForkJoinWorkerThreadFactoryImpl(),
            null,
            false
        )
    }

    /**
     * [ForkJoinWorkerThreadFactory] that doesn't change Permissions when [SecurityManager] is enabled.
     * ([ForkJoinPool]'s common pool uses a factory supplying threads that have no Permissions enabled if a
     * [SecurityManager] is present.)
     */
    private class ForkJoinWorkerThreadFactoryImpl : ForkJoinWorkerThreadFactory {

        override fun newThread(pool: ForkJoinPool?): ForkJoinWorkerThread {
            return ForkJoinWorkerThreadImpl(pool)
        }

        private class ForkJoinWorkerThreadImpl(pool: ForkJoinPool?) : ForkJoinWorkerThread(pool)
    }
}