package net.corda.ledger.verification.sandbox.factory

import net.corda.ledger.verification.sandbox.SandboxVerificationDependencyInjector
import net.corda.ledger.verification.sandbox.impl.SandboxVerificationDependencyInjectorImpl
import net.corda.sandbox.type.UsedByVerification
import net.corda.sandboxgroupcontext.CORDA_SANDBOX
import net.corda.sandboxgroupcontext.CORDA_SANDBOX_FILTER
import net.corda.sandboxgroupcontext.SandboxGroupContext
import net.corda.sandboxgroupcontext.SandboxGroupType.VERIFICATION
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.framework.Bundle
import org.osgi.framework.Constants
import org.osgi.framework.ServiceReference
import org.osgi.service.component.annotations.Component
import org.slf4j.LoggerFactory
import java.util.*

@Component(service = [SandboxVerificationDependencyInjectorFactory::class])
class SandboxVerificationDependencyInjectorFactoryImpl : SandboxVerificationDependencyInjectorFactory {
    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
        const val INJECTOR_FILTER = "(&$CORDA_SANDBOX_FILTER(${Constants.SERVICE_SCOPE}=${Constants.SCOPE_SINGLETON}))"
        private val FORBIDDEN_INTERFACES: Set<String> = Collections.unmodifiableSet(
            setOf(
                SingletonSerializeAsToken::class.java.name,
                UsedByVerification::class.java.name
            )
        )
    }

    override fun create(sandboxGroupContext: SandboxGroupContext): SandboxVerificationDependencyInjector {
        check(sandboxGroupContext.virtualNodeContext.sandboxGroupType === VERIFICATION) {
            "Expected serviceGroupType=${VERIFICATION} but found ${sandboxGroupContext.virtualNodeContext.sandboxGroupType}"
        }
        val references = LinkedList<ServiceReference<*>>()
        val sandboxGroup = sandboxGroupContext.sandboxGroup
        val sandboxId = sandboxGroup.id
        return sandboxGroup.metadata.keys.firstOrNull()
            ?.let(Bundle::getBundleContext)
            ?.let { bundleContext ->
                bundleContext.getServiceReferences(UsedByVerification::class.java, INJECTOR_FILTER)
                    .mapNotNull<ServiceReference<*>, Pair<SingletonSerializeAsToken, List<String>>> { ref ->
                        if (ref.getProperty(CORDA_SANDBOX) != sandboxId) {
                            // This shouldn't happen - it would imply our isolation hooks are buggy!
                            logger.warn("Service {}{} not applicable for sandbox '{}'", ref, ref.properties, sandboxId)
                            return@mapNotNull null
                        }

                        @Suppress("unchecked_cast")
                        (ref.getProperty(Constants.OBJECTCLASS) as? Array<String> ?: emptyArray<String>())
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
                        SandboxVerificationDependencyInjectorImpl(singletons) {
                            try {
                                references.forEach(bundleContext::ungetService)
                            } catch (e: IllegalStateException) {
                                logger.debug("{} already unloaded", sandboxGroupContext)
                            }
                        }
                    }
            } ?: SandboxVerificationDependencyInjectorImpl(emptyMap()) {}
    }
}