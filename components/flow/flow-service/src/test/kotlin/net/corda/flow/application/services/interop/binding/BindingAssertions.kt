package net.corda.flow.application.services.interop.binding

import net.corda.flow.application.services.impl.interop.binding.creation.FacadeInterfaceBindings
import net.corda.flow.application.services.impl.interop.binding.internal.FacadeInterfaceBindingException
import net.corda.v5.application.interop.facade.Facade
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertThrows


fun Facade.assertBindingFails(interfaceClass: Class<*>, expectedMessage: String) {
    val error = assertThrows<FacadeInterfaceBindingException> {
        FacadeInterfaceBindings.bind(this, interfaceClass)
    }
    assertTrue(error.message!!.contains(expectedMessage),"Expected Message was not present in : ${error.message}")
}
