package net.corda.crypto.service

import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorName

interface LifecycleNameProvider : Lifecycle {
    val lifecycleName: LifecycleCoordinatorName
}