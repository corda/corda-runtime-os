package net.corda.sandboxgroupcontext.service.impl

import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.utilities.trace
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.ServiceScope.SINGLETON
import org.slf4j.LoggerFactory

/**
 * A singleton service wrapping a [ThreadLocal] that contains the current [SandboxGroupContext].
 * Declare this service _explicitly_ as a [SINGLETON], just to make the point.
 *
 * FLOW sandboxes need this component to declare [SingletonSerializeAsToken] as one of its OSGi
 * services so that it can be registered with the Checkpoint serializer.
 */
@Component(service = [ CurrentSandboxGroupContext::class, SingletonSerializeAsToken::class ], scope = SINGLETON)
class CurrentSandboxGroupContextImpl : CurrentSandboxGroupContext, SingletonSerializeAsToken {
    private val log = LoggerFactory.getLogger(CurrentSandboxGroupContextImpl::class.java)

    private val currentSandboxGroupContext = ThreadLocal<SandboxGroupContext?>()

    override fun set(sandboxGroupContext: SandboxGroupContext) {
        log.info (
            "@@@ Setting current sandbox group context [$sandboxGroupContext] on thread [${Thread.currentThread().name}] for holding " +
                    "identity [${sandboxGroupContext.virtualNodeContext.holdingIdentity}]"
        )
        currentSandboxGroupContext.set(sandboxGroupContext)
    }

    override fun get(): SandboxGroupContext {
        val current = currentSandboxGroupContext.get()
        return if (current != null) {
            log.trace {
                "Retrieved current sandbox group context [$current] for thread [${Thread.currentThread().name}] with holding identity " +
                        "[${current.virtualNodeContext.holdingIdentity}]"
            }
            current
        } else {
            log.error("No current sandbox group context set for thread [${Thread.currentThread().name}]")
            throw IllegalStateException("No current sandbox group context set for thread")
        }
    }

    override fun remove() {
        val current = currentSandboxGroupContext.get()
        currentSandboxGroupContext.set(null)

        if (current != null) {
            log.info (
                "@@@ Removed current sandbox group context [$current] for thread [${Thread.currentThread().name}] with holding identity " +
                        "[${current.virtualNodeContext.holdingIdentity}]"
            )
        } else {
            // An exception is created here, so that the warning provides a stacktrace that can be used to determine where the thread
            // local is being incorrectly set or removed.
            // If the fiber fails to deserialize on
            log.info(
                "@@@ No current sandbox group context to remove for thread [${Thread.currentThread().name}]. This can happen if the fiber failed" +
                        "to deserialize on resume.",
                IllegalStateException("No current sandbox group context to remove for thread")
            )
        }
    }
}
