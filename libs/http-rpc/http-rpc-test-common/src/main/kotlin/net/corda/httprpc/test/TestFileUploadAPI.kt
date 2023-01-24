package net.corda.httprpc.test

import java.io.InputStream
import net.corda.httprpc.HttpFileUpload
import net.corda.httprpc.RestResource
import net.corda.httprpc.annotations.HttpRpcPOST
import net.corda.httprpc.annotations.HttpRpcPathParameter
import net.corda.httprpc.annotations.HttpRpcQueryParameter
import net.corda.httprpc.annotations.HttpRpcRequestBodyParameter
import net.corda.httprpc.annotations.HttpRpcResource

@HttpRpcResource(name = "TestFileUploadAPI", path = "fileupload/")
interface TestFileUploadAPI : RestResource {

    @HttpRpcPOST(path = "upload")
    fun upload(@HttpRpcRequestBodyParameter file: InputStream): String

    @HttpRpcPOST(path = "uploadWithName")
    fun uploadWithName(@HttpRpcRequestBodyParameter name: String, @HttpRpcRequestBodyParameter file: InputStream): String

    @HttpRpcPOST(path = "uploadWithoutParameterAnnotations")
    fun uploadWithoutParameterAnnotations(fileName: String, file: InputStream): String

    @HttpRpcPOST(path = "fileUploadObject")
    fun fileUpload(@HttpRpcRequestBodyParameter file: HttpFileUpload): String

    @HttpRpcPOST(path = "fileUploadWithFormParam")
    fun fileUploadWithFormParam(@HttpRpcRequestBodyParameter formParam: String, @HttpRpcRequestBodyParameter file: HttpFileUpload): String

    @HttpRpcPOST(path = "multiFileUploadObject")
    fun fileUpload(@HttpRpcRequestBodyParameter file1: HttpFileUpload, @HttpRpcRequestBodyParameter file2: HttpFileUpload): String

    @HttpRpcPOST(path = "fileUploadObjectList")
    fun fileUploadObjectList(@HttpRpcRequestBodyParameter files: List<HttpFileUpload>): String

    @HttpRpcPOST(path = "multiInputStreamFileUpload")
    fun multiInputStreamFileUpload(@HttpRpcRequestBodyParameter file1: InputStream, @HttpRpcRequestBodyParameter file2: InputStream): String

    @HttpRpcPOST(path = "uploadWithQueryParam")
    fun fileUploadWithQueryParam(
        @HttpRpcQueryParameter(required = false) tenant: String,
        @HttpRpcRequestBodyParameter file: HttpFileUpload
    ): String

    @HttpRpcPOST(path = "uploadWithPathParam/{tenant}/")
    fun fileUploadWithPathParam(
        @HttpRpcPathParameter tenant: String,
        @HttpRpcRequestBodyParameter file: HttpFileUpload
    ): String

    @HttpRpcPOST(path = "uploadWithNameInAnnotation")
    fun fileUploadWithNameInAnnotation(
        @HttpRpcRequestBodyParameter(name = "differentName", description = "differentDesc") file: HttpFileUpload
    ): String
}
