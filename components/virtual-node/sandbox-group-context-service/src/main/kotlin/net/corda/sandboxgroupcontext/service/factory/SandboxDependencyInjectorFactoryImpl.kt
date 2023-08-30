package net.corda.sandboxgroupcontext.service.factory

import net.corda.sandbox.type.UsedByFlow
import net.corda.sandbox.type.UsedByVerification
import net.corda.sandboxgroupcontext.CORDA_SANDBOX
import net.corda.sandboxgroupcontext.CORDA_SANDBOX_FILTER
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.service.SandboxDependencyInjector
import net.corda.sandboxgroupcontext.service.impl.SandboxDependencyInjectorImpl
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.framework.Bundle
import org.osgi.framework.Constants.OBJECTCLASS
import org.osgi.framework.Constants.SCOPE_SINGLETON
import org.osgi.framework.Constants.SERVICE_SCOPE
import org.osgi.framework.ServiceReference
import org.osgi.service.component.annotations.Component
import org.slf4j.LoggerFactory
import java.util.Collections.unmodifiableSet
import java.util.LinkedList

@Component(service = [SandboxDependencyInjectorFactory::class])
class SandboxDependencyInjectorFactoryImpl : SandboxDependencyInjectorFactory {
    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        private const val INJECTOR_FILTER = "(&$CORDA_SANDBOX_FILTER($SERVICE_SCOPE=$SCOPE_SINGLETON))"
        private val FORBIDDEN_INTERFACES: Set<String> = unmodifiableSet(setOf(
            SingletonSerializeAsToken::class.java.name,
            UsedByFlow::class.java.name,
            UsedByVerification::class.java.name
        ))
    }

    override fun <T: Any> create(sandboxGroupContext: SandboxGroupContext): SandboxDependencyInjector<T> {
        val sandboxGroupType = sandboxGroupContext.virtualNodeContext.sandboxGroupType

        require(sandboxGroupType.hasInjection) {
            "Expected serviceGroupType=${sandboxGroupType} doesn't support service injection"
        }
        val references = LinkedList<ServiceReference<*>>()
        val sandboxGroup = sandboxGroupContext.sandboxGroup
        val sandboxId = sandboxGroup.id
        val serviceMarkerType = sandboxGroupType.serviceMarkerType
        return sandboxGroup.metadata.keys.firstOrNull()
            ?.let(Bundle::getBundleContext)
            ?.let { bundleContext ->
                bundleContext.getServiceReferences(serviceMarkerType, INJECTOR_FILTER)
                    .mapNotNull<ServiceReference<*>, Pair<SingletonSerializeAsToken, List<String>>> { ref ->
                        if (ref.getProperty(CORDA_SANDBOX) != sandboxId) {
                            // This shouldn't happen - it would imply our isolation hooks are buggy!
                            logger.warn("Service {}{} not applicable for sandbox '{}'", ref, ref.properties, sandboxId)
                            return@mapNotNull null
                        }

                        @Suppress("unchecked_cast")
                        (ref.getProperty(OBJECTCLASS) as? Array<String> ?: emptyArray<String>())
                            .filterNot(FORBIDDEN_INTERFACES::contains)
                            .takeIf(List<*>::isNotEmpty)
                            ?.let { serviceTypes ->
                                bundleContext.getService(ref)?.let { service ->
                                    // We have a service for this reference, so remember it.
                                    references.addFirst(ref)
                                    if (service is SingletonSerializeAsToken) {
                                        service to serviceTypes
                                    } else {
                                        logger.error("Service {} is not serializable - SKIPPED", service::class.java.name)
                                        null
                                    }
                                }
                            }
                    }.toMap().let { singletons ->
                        SandboxDependencyInjectorImpl(singletons) {
                            try {
                                references.forEach(bundleContext::ungetService)
                            } catch (e: IllegalStateException) {
                                logger.debug("{} already unloaded", sandboxGroupContext)
                            }
                        }
                    }
            } ?: SandboxDependencyInjectorImpl(emptyMap()) {}
    }
}