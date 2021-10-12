package net.corda.classinfo.internal

import net.corda.classinfo.ClassInfoException
import net.corda.classinfo.ClassInfoService
import net.corda.sandbox.SandboxContextService
import net.corda.sandbox.SandboxException
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference

/**
 * An implementation of the [ClassInfoService] OSGi service interface.
 */
@Component(service = [ClassInfoService::class])
@Suppress("unused")
internal class ClassInfoServiceImpl @Activate constructor(
    @Reference
    private val sandboxContextService: SandboxContextService
) : ClassInfoService, SingletonSerializeAsToken {

    override fun getClassInfo(klass: Class<*>) = try {
        sandboxContextService.getClassInfo(klass)
    } catch (e: SandboxException) {
        throw ClassInfoException(e.message, e)
    }

    override fun getClassInfo(className: String) = try {
        sandboxContextService.getClassInfo(className)
    } catch (e: SandboxException) {
        throw ClassInfoException(e.message, e)
    }
}