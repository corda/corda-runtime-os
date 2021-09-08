package net.corda.sandbox.test

import net.corda.sandbox.SandboxGroup
import net.corda.v5.application.flows.Flow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.fail
import org.osgi.test.common.annotation.InjectService
import org.osgi.test.junit5.service.ServiceExtension

/** Tests the use of non-exported implementation classes from one bundle in another bundle. */
@ExtendWith(ServiceExtension::class)
class SandboxBundlePrivateImplementationTest {
    companion object {
        const val INVOKE_PRIVATE_IMPL_FLOW_CLASS = "com.example.sandbox.cpk1.InvokePrivateImplFlow"
        const val PRIVATE_IMPL_AS_GENERIC_FLOW_CLASS = "com.example.sandbox.cpk1.PrivateImplAsGenericFlow"
        const val PRIVATE_WRAPPER_RETURN_VALUE = "String returned by WrapperImpl."

        @InjectService(timeout = 1000)
        lateinit var sandboxLoader: SandboxLoader

        private fun runFlow(className: String, group: SandboxGroup): String {
            val workflowClass = group.loadClassFromCordappBundle(className, Flow::class.java)!!
            @Suppress("unchecked_cast")
            return sandboxLoader.getServiceFor(Flow::class.java, workflowClass).call() as? String
                ?: fail("Workflow does not return a List")
        }
    }

    @Test
    fun invokePrivateImplementationOfClass() {
        val returnString = runFlow(INVOKE_PRIVATE_IMPL_FLOW_CLASS, sandboxLoader.group1)
        assertEquals(PRIVATE_WRAPPER_RETURN_VALUE, returnString)
    }

    @Test
    fun usePrivateImplementationOfClassAsGeneric() {
        val returnString = runFlow(PRIVATE_IMPL_AS_GENERIC_FLOW_CLASS, sandboxLoader.group1)
        assertEquals(PRIVATE_WRAPPER_RETURN_VALUE, returnString)
    }
}
