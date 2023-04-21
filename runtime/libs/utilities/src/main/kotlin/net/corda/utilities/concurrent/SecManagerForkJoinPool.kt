package net.corda.utilities.concurrent

import java.util.concurrent.ForkJoinPool
import java.util.concurrent.ForkJoinPool.ForkJoinWorkerThreadFactory
import java.util.concurrent.ForkJoinWorkerThread

/**
 * [ForkJoinPool] that doesn't change permissions of threads when [SecurityManager] is enabled.
 */
class SecManagerForkJoinPool(val name: String): ForkJoinPool(
    Runtime.getRuntime().availableProcessors(),
    ForkJoinWorkerThreadFactoryImpl(name),
    null,
    false
) {
    companion object {
        val pool =  SecManagerForkJoinPool("SecManagerForkJoinPool")
    }

    /**
     * [ForkJoinWorkerThreadFactory] that doesn't change Permissions when [SecurityManager] is enabled.
     * ([ForkJoinPool]'s common pool uses a factory supplying threads that have no Permissions enabled if a
     * [SecurityManager] is present.)
     */
    private class ForkJoinWorkerThreadFactoryImpl(val poolName: String) : ForkJoinWorkerThreadFactory {

        override fun newThread(pool: ForkJoinPool?): ForkJoinWorkerThread {
            return ForkJoinWorkerThreadImpl(pool).apply {
                this.name = "$poolName-worker-" + this.poolIndex
            }
        }

        private class ForkJoinWorkerThreadImpl(pool: ForkJoinPool?) : ForkJoinWorkerThread(pool)
    }
}