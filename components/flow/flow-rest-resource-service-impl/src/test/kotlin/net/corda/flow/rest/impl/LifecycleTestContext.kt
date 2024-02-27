package net.corda.flow.rest.impl

import net.corda.lifecycle.LifecycleCoordinator
import net.corda.lifecycle.LifecycleCoordinatorFactory
import net.corda.lifecycle.LifecycleEventHandler
import net.corda.lifecycle.RegistrationHandle
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

internal class LifecycleTestContext {
    val lifecycleCoordinatorFactory = mock<LifecycleCoordinatorFactory>()
    val lifecycleCoordinator = mock<LifecycleCoordinator>()
    private val lifecycleEventRegistration = mock<RegistrationHandle>()

    private var eventHandler: LifecycleEventHandler? = null

    init {
        whenever(lifecycleCoordinatorFactory.createCoordinator(any(), any())).thenReturn(lifecycleCoordinator)
        whenever(lifecycleCoordinator.followStatusChangesByName(any())).thenReturn(lifecycleEventRegistration)
    }

    fun getEventHandler(): LifecycleEventHandler {
        if (eventHandler == null) {
            argumentCaptor<LifecycleEventHandler>().apply {
                verify(lifecycleCoordinatorFactory).createCoordinator(
                    any(),
                    capture()
                )
                eventHandler = firstValue
            }
        }

        return eventHandler!!
    }
}