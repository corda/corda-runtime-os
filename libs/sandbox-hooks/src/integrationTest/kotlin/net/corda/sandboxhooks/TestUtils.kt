package net.corda.sandboxhooks

import net.corda.sandbox.SandboxGroup
import net.corda.v5.application.flows.Flow
import org.junit.jupiter.api.fail
import org.osgi.framework.Bundle
import org.osgi.framework.FrameworkUtil

/** Loads the flow with [className] from sandbox group [group], runs it, and casts the result to [T]. */
internal fun <T : Any> runFlow(group: SandboxGroup, className: String): T {
    val flowClass = group.loadClassFromMainBundles(className, Flow::class.java)

    val context = FrameworkUtil.getBundle(flowClass).bundleContext
    val flowServices = context.getServiceReferences(Flow::class.java, null).map(context::getService)

    val flow = flowServices.filterIsInstance(flowClass).firstOrNull() ?: fail("No service for $flowClass.")

    @Suppress("unchecked_cast")
    return flow.call() as? T ?: fail("Workflow did not return the correct type.")
}

/** Indicates whether the [sandboxGroup] contains the [bundle]. */
internal fun sandboxGroupContainsBundle(sandboxGroup: SandboxGroup, bundle: Bundle): Boolean {
    val sandboxesMethod = sandboxGroup::class.java.getMethod("getSandboxes")
    @Suppress("UNCHECKED_CAST")
    val sandboxes = sandboxesMethod.invoke(sandboxGroup) as Collection<Any>
    return sandboxes.any { sandbox -> sandboxContainsBundle(sandbox, bundle) }
}

/** Indicates whether the [sandbox] contains the [bundle]. */
private fun sandboxContainsBundle(sandbox: Any, bundle: Bundle): Boolean {
    val containsMethod = sandbox::class.java.getMethod("containsBundle", Bundle::class.java)
    return containsMethod.invoke(sandbox, bundle) as Boolean
}