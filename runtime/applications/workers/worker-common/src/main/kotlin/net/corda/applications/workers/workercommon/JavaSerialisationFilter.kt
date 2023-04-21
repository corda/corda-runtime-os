package net.corda.applications.workers.workercommon

import java.io.ObjectInputFilter
import java.lang.reflect.Proxy
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object JavaSerialisationFilter {
    val lock = ReentrantLock()
    var installed = false
    private val rules = listOf<(Class<*>) -> Boolean>(
        {
            var componentType: Class<*> = it
            while (componentType.isArray) componentType = componentType.componentType
            componentType.isPrimitive
        },
        { it.name.startsWith("oracle.sql.converter.") }
    )

    fun install() {
        if (installed)
            return
        lock.withLock {
            if(installed)
                return

            val filter = Proxy.newProxyInstance(
                javaClass.classLoader,
                arrayOf(ObjectInputFilter::class.java)
            ) { _, _, args ->
                val serialClass = ObjectInputFilter.FilterInfo::serialClass.invoke(args[0] as ObjectInputFilter.FilterInfo)
                if (null == serialClass || rules.any { it.invoke(serialClass) }) {
                    ObjectInputFilter.Status.UNDECIDED
                } else {
                    ObjectInputFilter.Status.REJECTED
                }
            } as ObjectInputFilter
            ObjectInputFilter.Config.setSerialFilter(filter)
            installed = true
        }
    }
}