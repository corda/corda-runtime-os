package net.corda.flow.application.services.interop.binding

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.string.shouldContain
import net.corda.flow.application.services.impl.interop.binding.creation.FacadeInterfaceBindings
import net.corda.flow.application.services.impl.interop.binding.internal.FacadeInterfaceBindingException
import net.corda.v5.application.interop.facade.Facade

fun Facade.assertBindingFails(interfaceClass: Class<*>, expectedMessage: String) {
    shouldThrow<FacadeInterfaceBindingException> {
        FacadeInterfaceBindings.bind(this, interfaceClass)
    }.message shouldContain expectedMessage
}