package net.corda.rest.test

import java.io.InputStream
import net.corda.rest.HttpFileUpload
import net.corda.rest.RestResource
import net.corda.rest.annotations.HttpPOST
import net.corda.rest.annotations.RestPathParameter
import net.corda.rest.annotations.RestQueryParameter
import net.corda.rest.annotations.ClientRequestBodyParameter
import net.corda.rest.annotations.HttpRestResource

@HttpRestResource(name = "TestFileUploadAPI", path = "fileupload/")
interface TestFileUploadAPI : RestResource {

    @HttpPOST(path = "upload")
    fun upload(@ClientRequestBodyParameter file: InputStream): String

    @HttpPOST(path = "uploadWithName")
    fun uploadWithName(@ClientRequestBodyParameter name: String, @ClientRequestBodyParameter file: InputStream): String

    @HttpPOST(path = "uploadWithoutParameterAnnotations")
    fun uploadWithoutParameterAnnotations(fileName: String, file: InputStream): String

    @HttpPOST(path = "fileUploadObject")
    fun fileUpload(@ClientRequestBodyParameter file: HttpFileUpload): String

    @HttpPOST(path = "fileUploadWithFormParam")
    fun fileUploadWithFormParam(@ClientRequestBodyParameter formParam: String, @ClientRequestBodyParameter file: HttpFileUpload): String

    @HttpPOST(path = "multiFileUploadObject")
    fun fileUpload(@ClientRequestBodyParameter file1: HttpFileUpload, @ClientRequestBodyParameter file2: HttpFileUpload): String

    @HttpPOST(path = "fileUploadObjectList")
    fun fileUploadObjectList(@ClientRequestBodyParameter files: List<HttpFileUpload>): String

    @HttpPOST(path = "multiInputStreamFileUpload")
    fun multiInputStreamFileUpload(@ClientRequestBodyParameter file1: InputStream, @ClientRequestBodyParameter file2: InputStream): String

    @HttpPOST(path = "uploadWithQueryParam")
    fun fileUploadWithQueryParam(
        @RestQueryParameter(required = false) tenant: String,
        @ClientRequestBodyParameter file: HttpFileUpload
    ): String

    @HttpPOST(path = "uploadWithPathParam/{tenant}/")
    fun fileUploadWithPathParam(
        @RestPathParameter tenant: String,
        @ClientRequestBodyParameter file: HttpFileUpload
    ): String

    @HttpPOST(path = "uploadWithNameInAnnotation")
    fun fileUploadWithNameInAnnotation(
        @ClientRequestBodyParameter(name = "differentName", description = "differentDesc") file: HttpFileUpload
    ): String
}
