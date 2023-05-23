package net.corda.flow.application.services.interop.binding

import net.corda.flow.application.services.impl.interop.binding.creation.FacadeInterfaceBindings
import net.corda.flow.application.services.impl.interop.binding.internal.FacadeInterfaceBindingException
import net.corda.v5.application.interop.facade.Facade
import org.junit.jupiter.api.assertThrows


fun Facade.assertBindingFails(interfaceClass: Class<*>, expectedMessage: String) {
    assertThrows<FacadeInterfaceBindingException> {
        FacadeInterfaceBindings.bind(this, interfaceClass)
    }.message.equals(expectedMessage)
}
