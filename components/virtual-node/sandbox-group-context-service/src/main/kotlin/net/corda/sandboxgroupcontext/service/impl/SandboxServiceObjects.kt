package net.corda.sandboxgroupcontext.service.impl

import org.osgi.framework.ServiceObjects
import org.osgi.framework.ServiceReference
import org.slf4j.LoggerFactory
import java.lang.reflect.InvocationTargetException

class SandboxServiceObjects(
    private val reference: ServiceReference<*>,
    private val definition: ServiceDefinition,
    private val sandboxServices: SatisfiedServiceReferences
) : ServiceObjects<Any> {
    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val closeables = mutableListOf<AutoCloseable>()

    override fun getServiceReference(): ServiceReference<Any> {
        @Suppress("unchecked_cast")
        return reference as ServiceReference<Any>
    }

    override fun getService(): Any? {
        return try {
            return definition.createInstance(serviceReference.bundle, sandboxServices).let { svc ->
                closeables.addAll(svc.second)
                svc.first
            }
        } catch (e: Exception) {
            val cause = (e as? InvocationTargetException)?.targetException ?: e
            logger.error("Failed to create sandbox service $reference", cause)
            null
        }
    }

    override fun ungetService(obj: Any?) {
        closeables.forEach(::closeSafely)
    }
}
