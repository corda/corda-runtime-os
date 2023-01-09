package net.corda.lifecycle.domino.logic

import net.corda.lifecycle.Lifecycle
import net.corda.lifecycle.LifecycleCoordinatorName

data class NamedLifecycle(
    val lifecycle: Lifecycle,
    val name: LifecycleCoordinatorName
) {
    companion object {
        inline fun <reified T: Lifecycle> of(item: T): NamedLifecycle {
            return NamedLifecycle(
                item,
                LifecycleCoordinatorName.forComponent<T>()
            )
        }
    }
}
