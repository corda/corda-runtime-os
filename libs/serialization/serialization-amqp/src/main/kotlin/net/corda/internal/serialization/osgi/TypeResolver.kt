package net.corda.internal.serialization.osgi

import net.corda.classinfo.ClassInfoException
import net.corda.classinfo.ClassInfoService
import net.corda.internal.serialization.amqp.asClass
import net.corda.sandbox.ClassInfo
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import java.lang.reflect.Type

@Component(immediate = true, service = [TypeResolver::class])
class TypeResolver @Activate constructor(
        @Reference
        classInfoService: ClassInfoService
) {

    companion object {
        private var classInfoService: ClassInfoService? = null

        fun getClassInfoFor(type: Type): ClassInfo? = classInfoService?.getClassInfo(type.asClass())

        fun resolve(className: String, classLoader: ClassLoader): Class<*> {
            return try {
                //classInfoService?.getPlatformClass(className) as Class<*>
                Class.forName(className, false, classLoader)
            } catch (ex: Exception) {
                when (ex) {
                    is ClassInfoException, is NullPointerException -> Class.forName(className, false, classLoader)
                    else -> throw ex
                }
            }
        }
    }

    init {
        TypeResolver.classInfoService = classInfoService
    }
}