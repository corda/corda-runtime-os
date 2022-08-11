package net.corda.osgi.framework

import java.util.concurrent.ForkJoinPool
import java.util.concurrent.ForkJoinPool.ForkJoinWorkerThreadFactory
import java.util.concurrent.ForkJoinWorkerThread

/**
 * [ForkJoinWorkerThreadFactory] that doesn't change Permissions when [SecurityManager] is enabled.
 * ([ForkJoinPool]'s common pool uses a factory supplying threads that have no Permissions enabled if a
 * [SecurityManager] is present.)
 */
class SecManagerForkJoinWorkerThreadFactory : ForkJoinWorkerThreadFactory {

    override fun newThread(pool: ForkJoinPool?): ForkJoinWorkerThread {
        return SecManagerForkJoinWorkerThread(pool)
    }

    private class SecManagerForkJoinWorkerThread(pool: ForkJoinPool?) : ForkJoinWorkerThread(pool)
}