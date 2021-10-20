package net.corda.securitymanager.invokerimpl

import net.corda.securitymanager.invoker.Invoker
import org.osgi.service.component.annotations.Component

/** An implementation of the [Invoker] interface. */
@Component
@Suppress("unused")
class InvokerImpl: Invoker {
    override fun performAction(lambda: () -> Unit) = lambda()
}