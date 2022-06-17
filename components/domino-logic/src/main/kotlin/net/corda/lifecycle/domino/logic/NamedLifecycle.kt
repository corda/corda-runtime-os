package net.corda.lifecycle.domino.logic

import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorName

data class NamedLifecycle(
    val lifecycle: Lifecycle,
    val name: LifecycleCoordinatorName
)
