package net.corda.httprpc.test

import java.io.InputStream
import net.corda.httprpc.HttpFileUpload
import net.corda.httprpc.RestResource
import net.corda.httprpc.annotations.HttpPOST
import net.corda.httprpc.annotations.RestPathParameter
import net.corda.httprpc.annotations.RestQueryParameter
import net.corda.httprpc.annotations.RestRequestBodyParameter
import net.corda.httprpc.annotations.HttpRestResource

@HttpRestResource(name = "TestFileUploadAPI", path = "fileupload/")
interface TestFileUploadAPI : RestResource {

    @HttpPOST(path = "upload")
    fun upload(@RestRequestBodyParameter file: InputStream): String

    @HttpPOST(path = "uploadWithName")
    fun uploadWithName(@RestRequestBodyParameter name: String, @RestRequestBodyParameter file: InputStream): String

    @HttpPOST(path = "uploadWithoutParameterAnnotations")
    fun uploadWithoutParameterAnnotations(fileName: String, file: InputStream): String

    @HttpPOST(path = "fileUploadObject")
    fun fileUpload(@RestRequestBodyParameter file: HttpFileUpload): String

    @HttpPOST(path = "fileUploadWithFormParam")
    fun fileUploadWithFormParam(@RestRequestBodyParameter formParam: String, @RestRequestBodyParameter file: HttpFileUpload): String

    @HttpPOST(path = "multiFileUploadObject")
    fun fileUpload(@RestRequestBodyParameter file1: HttpFileUpload, @RestRequestBodyParameter file2: HttpFileUpload): String

    @HttpPOST(path = "fileUploadObjectList")
    fun fileUploadObjectList(@RestRequestBodyParameter files: List<HttpFileUpload>): String

    @HttpPOST(path = "multiInputStreamFileUpload")
    fun multiInputStreamFileUpload(@RestRequestBodyParameter file1: InputStream, @RestRequestBodyParameter file2: InputStream): String

    @HttpPOST(path = "uploadWithQueryParam")
    fun fileUploadWithQueryParam(
        @RestQueryParameter(required = false) tenant: String,
        @RestRequestBodyParameter file: HttpFileUpload
    ): String

    @HttpPOST(path = "uploadWithPathParam/{tenant}/")
    fun fileUploadWithPathParam(
        @RestPathParameter tenant: String,
        @RestRequestBodyParameter file: HttpFileUpload
    ): String

    @HttpPOST(path = "uploadWithNameInAnnotation")
    fun fileUploadWithNameInAnnotation(
        @RestRequestBodyParameter(name = "differentName", description = "differentDesc") file: HttpFileUpload
    ): String
}
