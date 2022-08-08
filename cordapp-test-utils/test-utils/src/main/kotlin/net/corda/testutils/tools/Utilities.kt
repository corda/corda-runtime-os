package net.corda.testutils.tools

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.Flow
import java.lang.reflect.Modifier

fun <T> Flow.injectIfRequired(
    field: Class<T>,
    value: T
) = this.javaClass.declaredFields.firstOrNull {
    println("Field ${it.name} is ${Modifier.toString(it.modifiers)} and of type ${it.type}")
    it.type.equals(field) && it.canAccess(this) && it.isAnnotationPresent(CordaInject::class.java)
}?.set(this, value)