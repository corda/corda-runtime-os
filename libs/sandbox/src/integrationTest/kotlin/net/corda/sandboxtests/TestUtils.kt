package net.corda.sandboxtests

import net.corda.sandbox.SandboxGroup
import net.corda.v5.application.flows.Flow
import org.junit.jupiter.api.fail
import org.osgi.framework.Bundle
import org.osgi.framework.FrameworkUtil
import java.util.function.Function

/** Loads the [Flow] with [className] from the sandbox [group], runs it, and casts the result to [T]. */
internal fun <T : Any> runFlow(group: SandboxGroup, className: String): T {
    val flowClass = group.loadClassFromMainBundles(className, Flow::class.java)

    val context = FrameworkUtil.getBundle(flowClass).bundleContext
    val flowServices = context.getServiceReferences(Flow::class.java, null).map(context::getService)

    val flow = flowServices.filterIsInstance(flowClass).firstOrNull() ?: fail("No service for $flowClass.")

    @Suppress("unchecked_cast")
    return flow.call() as? T ?: fail("Workflow did not return the correct type.")
}

/** Loads the [Function] with [className] from the sandbox [group], and applies it with [argument]. */
internal fun <U: Any, T: Any> applyFunction(group: SandboxGroup, className: String, argument: U): T {
    val functionClass = group.loadClassFromMainBundles(className, Function::class.java)

    val context = FrameworkUtil.getBundle(functionClass).bundleContext
    val flowServices = context.getServiceReferences(Function::class.java, null).map(context::getService)

    val flow = flowServices.filterIsInstance(functionClass).firstOrNull() ?: fail("No service for $functionClass.")

    @Suppress("unchecked_cast")
    return (flow as Function<U, T>).apply(argument)
}

/** Indicates whether the [sandboxGroup] contains the [bundle]. */
internal fun sandboxGroupContainsBundle(sandboxGroup: SandboxGroup, bundle: Bundle): Boolean {
    val sandboxesMethod = sandboxGroup::class.java.getMethod("getCpkSandboxes")
    @Suppress("UNCHECKED_CAST")
    val sandboxes = sandboxesMethod.invoke(sandboxGroup) as Collection<Any>
    return sandboxes.any { sandbox -> sandboxContainsBundle(sandbox, bundle) }
}

/** Indicates whether the [sandbox] contains the [bundle]. */
private fun sandboxContainsBundle(sandbox: Any, bundle: Bundle): Boolean {
    val containsMethod = sandbox::class.java.getMethod("containsBundle", Bundle::class.java)
    return containsMethod.invoke(sandbox, bundle) as Boolean
}