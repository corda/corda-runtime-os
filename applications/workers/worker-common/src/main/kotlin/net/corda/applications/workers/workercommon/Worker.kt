package net.corda.applications.workers.workercommon

import net.corda.applications.workers.workercommon.internal.HealthProviderImpl
import net.corda.osgi.api.Application

// TODO - Describe.
abstract class Worker: Application {
    protected val healthProvider: HealthProvider = HealthProviderImpl()

    init {
        healthProvider.setIsHealthy()
    }
}