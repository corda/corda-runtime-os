package net.corda.crypto.component.impl

import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorName

interface LifecycleNameProvider : Lifecycle {
    val lifecycleName: LifecycleCoordinatorName
}

fun LifecycleNameProvider.lifecycleNameAsSet(): Set<LifecycleCoordinatorName> = setOf(lifecycleName)