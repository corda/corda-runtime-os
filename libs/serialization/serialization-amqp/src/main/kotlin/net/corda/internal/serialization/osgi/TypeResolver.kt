package net.corda.internal.serialization.osgi

import net.corda.internal.serialization.amqp.asClass
import net.corda.sandbox.SandboxContextService
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.lang.reflect.Type

@Component(immediate = true, service = [TypeResolver::class])
class TypeResolver @Activate constructor(
        @Reference
        sandboxContextService: SandboxContextService
) {

    companion object {
        private var sandboxContextService: SandboxContextService? = null

        fun getClassTagFor(type: Type): String = sandboxContextService!!.getClassTag(type.asClass(), isStaticTag = false)

        fun resolve(className: String, classLoader: ClassLoader): Class<*> {
            return try {
                //classInfoService?.getPlatformClass(className) as Class<*>
                Class.forName(className, false, classLoader)
            } catch (ex: Exception) {
                when (ex) {
                    is NullPointerException -> Class.forName(className, false, classLoader)
                    else -> throw ex
                }
            }
        }
    }

    init {
        TypeResolver.sandboxContextService = sandboxContextService
    }
}