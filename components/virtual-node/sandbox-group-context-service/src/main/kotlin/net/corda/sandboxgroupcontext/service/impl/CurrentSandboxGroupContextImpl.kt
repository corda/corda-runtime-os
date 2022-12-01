package net.corda.sandboxgroupcontext.service.impl

import net.corda.sandbox.type.UsedByFlow
import net.corda.sandboxgroupcontext.CurrentSandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.trace
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Component

@Component(service = [CurrentSandboxGroupContext::class, UsedByFlow::class])
class CurrentSandboxGroupContextImpl : CurrentSandboxGroupContext, SingletonSerializeAsToken, UsedByFlow {

    private companion object {
        @JvmField
        val log = contextLogger()
        @JvmField
        val currentSandboxGroupContext = ThreadLocal<SandboxGroupContext?>()
    }

    override fun set(sandboxGroupContext: SandboxGroupContext) {
        log.trace {
            "Setting current sandbox group context [$sandboxGroupContext] on thread [${Thread.currentThread().name}] for holding " +
                    "identity [${sandboxGroupContext.virtualNodeContext.holdingIdentity}]"
        }
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
            log.trace {
                "Removed current sandbox group context [$current] for thread [${Thread.currentThread().name}] with holding identity " +
                        "[${current.virtualNodeContext.holdingIdentity}]"
            }
        } else {
            // An exception is created here, so that the warning provides a stacktrace that can be used to determine where the thread
            // local is being incorrectly set or removed.
            log.warn(
                "No current sandbox group context to remove for thread [${Thread.currentThread().name}]",
                IllegalStateException("No current sandbox group context to remove for thread")
            )
        }
    }
}