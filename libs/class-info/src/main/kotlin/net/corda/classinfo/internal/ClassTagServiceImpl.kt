package net.corda.classinfo.internal

import net.corda.classinfo.ClassTagException
import net.corda.classinfo.ClassTagService
import net.corda.sandbox.SandboxContextService
import net.corda.sandbox.SandboxException
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/**
 * An implementation of the [ClassTagService] OSGi service interface.
 */
@Component(service = [ClassTagService::class])
@Suppress("unused")
internal class ClassTagServiceImpl @Activate constructor(
    @Reference
    private val sandboxContextService: SandboxContextService
) : ClassTagService, SingletonSerializeAsToken {

    override fun getClassTag(klass: Class<*>, isStaticTag: Boolean) = try {
        sandboxContextService.getClassTag(klass, isStaticTag)
    } catch (e: SandboxException) {
        throw ClassTagException(e.message, e)
    }

    override fun getClassTag(className: String, isStaticTag: Boolean) = try {
        sandboxContextService.getClassTag(className, isStaticTag)
    } catch (e: SandboxException) {
        throw ClassTagException(e.message, e)
    }
}