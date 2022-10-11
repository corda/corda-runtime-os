package net.corda.sandboxgroupcontext.service.impl

import net.corda.sandboxgroupcontext.SandboxGroupContextService
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.trace
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Component

@Component(service = [SandboxGroupContextService::class, SingletonSerializeAsToken::class])
class SandboxGroupContextServiceImpl : SandboxGroupContextService, SingletonSerializeAsToken {

    private companion object {
        val log = contextLogger()
    }

    override fun setCurrent(sandboxGroupContext: SandboxGroupContext) {
        log.trace {
            "Setting current sandbox group context [$sandboxGroupContext] on thread [${Thread.currentThread().name}] for holding " +
                    "identity [${sandboxGroupContext.virtualNodeContext.holdingIdentity}]"
        }
        currentSandboxGroupContext.set(sandboxGroupContext)
    }

    override fun getCurrent(): SandboxGroupContext {
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

    override fun removeCurrent() {
        val current = currentSandboxGroupContext.get()
        currentSandboxGroupContext.remove()

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

private val currentSandboxGroupContext = ThreadLocal<SandboxGroupContext>()