//package net.corda.sandbox.test
//
//import org.junit.jupiter.api.Assertions.assertEquals
//import org.junit.jupiter.api.Test
//import org.junit.jupiter.api.extension.ExtendWith
//import org.osgi.test.common.annotation.InjectService
//import org.osgi.test.junit5.service.ServiceExtension
//
///** Tests the use of non-exported implementation classes from one bundle in another bundle. */
//@ExtendWith(ServiceExtension::class)
//class SandboxBundlePrivateImplementationTest {
//    companion object {
//        const val INVOKE_PRIVATE_IMPL_FLOW_CLASS = "com.example.sandbox.cpk1.InvokePrivateImplFlow"
//        const val PRIVATE_IMPL_AS_GENERIC_FLOW_CLASS = "com.example.sandbox.cpk1.PrivateImplAsGenericFlow"
//        const val PRIVATE_WRAPPER_RETURN_VALUE = "String returned by WrapperImpl."
//
//        @InjectService(timeout = 1000)
//        lateinit var sandboxLoader: SandboxLoader
//    }
//
//    @Test
//    fun invokePrivateImplementationOfClass() {
//        val returnString = sandboxLoader.runFlow<String>(INVOKE_PRIVATE_IMPL_FLOW_CLASS, sandboxLoader.group1)
//        assertEquals(PRIVATE_WRAPPER_RETURN_VALUE, returnString)
//    }
//
//    @Test
//    fun usePrivateImplementationOfClassAsGeneric() {
//        val returnString = sandboxLoader.runFlow<String>(PRIVATE_IMPL_AS_GENERIC_FLOW_CLASS, sandboxLoader.group1)
//        assertEquals(PRIVATE_WRAPPER_RETURN_VALUE, returnString)
//    }
//}
