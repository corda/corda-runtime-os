package net.corda.flow.pipeline.sandbox.factory

import net.corda.flow.pipeline.sandbox.SandboxDependencyInjector
import net.corda.flow.pipeline.sandbox.impl.SandboxDependencyInjectorImpl
import net.corda.sandbox.type.UsedByFlow
import net.corda.sandboxgroupcontext.CORDA_SANDBOX_FILTER
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupType.FLOW
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.framework.Bundle
import org.osgi.framework.Constants.OBJECTCLASS
import org.osgi.framework.Constants.SCOPE_SINGLETON
import org.osgi.framework.Constants.SERVICE_SCOPE
import org.osgi.framework.ServiceReference
import org.osgi.service.component.annotations.Component
import java.util.Collections.unmodifiableSet
import java.util.LinkedList

@Component(service = [SandboxDependencyInjectorFactory::class])
class SandboxDependencyInjectorFactoryImpl : SandboxDependencyInjectorFactory {
    private companion object {
        private const val INJECTOR_FILTER = "(&$CORDA_SANDBOX_FILTER($SERVICE_SCOPE=$SCOPE_SINGLETON))"
        private val FORBIDDEN_INTERFACES: Set<String> = unmodifiableSet(setOf(
            SingletonSerializeAsToken::class.java.name,
            UsedByFlow::class.java.name
        ))
    }

    override fun create(sandboxGroupContext: SandboxGroupContext): SandboxDependencyInjector {
        check(sandboxGroupContext.virtualNodeContext.sandboxGroupType === FLOW) {
            "Expected serviceGroupType=$FLOW but found ${sandboxGroupContext.virtualNodeContext.sandboxGroupType}"
        }
        val references = LinkedList<ServiceReference<*>>()
        return sandboxGroupContext.sandboxGroup.metadata.keys.firstOrNull()
            ?.let(Bundle::getBundleContext)
            ?.let { bundleContext ->
                bundleContext.getServiceReferences(UsedByFlow::class.java, INJECTOR_FILTER).mapNotNull { ref ->
                    @Suppress("unchecked_cast", "RemoveExplicitTypeArguments")
                    (ref.getProperty(OBJECTCLASS) as? Array<String> ?: emptyArray<String>())
                        .filterNot(FORBIDDEN_INTERFACES::contains)
                        .takeIf(List<*>::isNotEmpty)
                        ?.let { serviceTypes ->
                            bundleContext.getService(ref)?.let { service ->
                                // We have a service for this reference, so remember it.
                                references.addFirst(ref)
                                service to serviceTypes
                            }
                        }
                }.toMap().let { singletons ->
                    SandboxDependencyInjectorImpl(singletons) {
                        references.forEach(bundleContext::ungetService)
                    }
                }
            } ?: SandboxDependencyInjectorImpl(emptyMap()) {}
    }
}
