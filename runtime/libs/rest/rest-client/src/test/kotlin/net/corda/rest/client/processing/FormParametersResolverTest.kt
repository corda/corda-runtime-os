package net.corda.rest.client.processing

import org.junit.jupiter.api.Test
import kotlin.reflect.jvm.javaMethod
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import net.corda.rest.HttpFileUpload
import net.corda.rest.test.TestFileUploadAPI

class FormParametersResolverTest {
    @Test
    fun `test formParametersResolver using multipart file upload method with both a form parameter and file`() {
        val result = TestFileUploadAPI::uploadWithName.javaMethod!!.formParametersFrom(arrayOf("abc", "def".byteInputStream()))

        assertNotNull(result["name"])
        assertEquals("abc", result["name"])
    }

    @Test
    fun `test form parameters from multipart file upload method with a HttpFileUpload and form param`() {
        val stream = "def".byteInputStream()
        val file = HttpFileUpload(stream, "filename.xml")
        val result = TestFileUploadAPI::fileUploadWithFormParam.javaMethod!!.formParametersFrom(arrayOf("abc", file))

        assertNotNull(result["formParam"])
        assertEquals("abc", result["formParam"])
    }
}