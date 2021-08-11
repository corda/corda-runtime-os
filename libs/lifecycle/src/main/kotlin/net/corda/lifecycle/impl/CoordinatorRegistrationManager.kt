package net.corda.lifecycle.impl

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleState
import java.util.concurrent.ConcurrentHashMap

internal class CoordinatorRegistrationManager {

    private val registrations: MutableMap<CoordinatorStateRegistration, Unit> = ConcurrentHashMap()

    private val coordinatorToRegistration: MutableMap<LifecycleCoordinator, MutableList<CoordinatorStateRegistration>> = ConcurrentHashMap()

    fun newRegistration(registration: CoordinatorStateRegistration)  {
        registration.coordinators.forEach {
            coordinatorToRegistration.compute(it) { _, currentList ->
                val list = currentList ?: mutableListOf()
                list.add(registration)
                list
            }
        }
        registrations[registration] = Unit
        registration.setupRegistration()
    }

    fun cancelRegistration(registration: CoordinatorStateRegistration) {
        registration.coordinators.forEach {
            coordinatorToRegistration.compute(it) { _, currentList ->
                currentList?.remove(registration)
                if (currentList != null && currentList.isNotEmpty()) {
                    currentList
                } else {
                    null
                }
            }
        }
        registrations.remove(registration)
    }

    fun updateRegistrations(coordinator: LifecycleCoordinator, newState: LifecycleState) {
        coordinatorToRegistration[coordinator]?.forEach {
            it.updateCoordinatorState(coordinator, newState)
        }
    }
}