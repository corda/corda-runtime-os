package net.corda.flow.pipeline.sandbox.factory

import net.corda.flow.pipeline.sandbox.SandboxDependencyInjector
import net.corda.flow.pipeline.sandbox.impl.SandboxDependencyInjectorImpl
import net.corda.sandboxgroupcontext.CORDA_SANDBOX_FILTER
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.framework.Bundle
import org.osgi.framework.Constants.OBJECTCLASS
import org.osgi.framework.Constants.SCOPE_SINGLETON
import org.osgi.framework.Constants.SERVICE_SCOPE
import org.osgi.service.component.annotations.Component

@Component(service = [SandboxDependencyInjectorFactory::class])
class SandboxDependencyInjectorFactoryImpl : SandboxDependencyInjectorFactory {
    private companion object {
        private const val INJECTOR_FILTER = "(&$CORDA_SANDBOX_FILTER($SERVICE_SCOPE=$SCOPE_SINGLETON))"
    }

    override fun create(sandboxGroupContext: SandboxGroupContext): SandboxDependencyInjector {
        check(sandboxGroupContext.virtualNodeContext.serviceMarkerType === SingletonSerializeAsToken::class.java) {
            "Expected serviceMarkerType=${SingletonSerializeAsToken::class.java.name} " +
                    "but found ${sandboxGroupContext.virtualNodeContext.serviceMarkerType.name}"
        }
        return sandboxGroupContext.sandboxGroup.metadata.keys.firstOrNull()
            ?.let(Bundle::getBundleContext)
            ?.let { bundleContext ->
                val references = bundleContext.getServiceReferences(SingletonSerializeAsToken::class.java, INJECTOR_FILTER)
                references.mapNotNull { ref ->
                    @Suppress("unchecked_cast")
                    bundleContext.getService(ref)?.let { service ->
                        @Suppress("RemoveExplicitTypeArguments")
                        service to (ref.getProperty(OBJECTCLASS) as? Array<String> ?: emptyArray<String>())
                    }
                }.toMap().let { singletons ->
                    SandboxDependencyInjectorImpl(singletons) {
                        references.forEach(bundleContext::ungetService)
                    }
                }
            } ?: SandboxDependencyInjectorImpl(emptyMap()) {}
    }
}
