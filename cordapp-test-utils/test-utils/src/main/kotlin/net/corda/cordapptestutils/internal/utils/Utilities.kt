package net.corda.cordapptestutils.internal.utils

import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.Flow
import net.corda.v5.base.types.MemberX500Name

fun <T> Flow.injectIfRequired(
    field: Class<T>,
    value: T
) = this.javaClass.declaredFields.firstOrNull {
    it.type.equals(field) && it.canAccess(this) && it.isAnnotationPresent(CordaInject::class.java)
}?.set(this, value)

val MemberX500Name.sandboxName: Any
    get() {
        return this.toString()
            .replace("=", "_")
            .replace(" ", "_")
            .replace(",", "")
    }
