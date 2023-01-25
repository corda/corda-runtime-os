package net.corda.httprpc

import java.io.InputStream

/**
 * This object allows custom [RestResource] endpoints to upload a file and gives the implementation a handle on the
 * file's content and metadata.
 *
 * To add file upload to an [RestResource] endpoint, declare a parameter with this type on the function with the
 * request body annotation
 * [net.corda.httprpc.annotations.RestRequestBodyParameter].
 *
 * Alternatively, if the extra metadata is not necessary and only the file content is required, you can use declare
 * a parameter of type
 * [InputStream] instead.
 *
 * Example usage:
 * ```
 * @HttpPOST(path = "fileUpload")
 * fun fileUpload(@RestRequestBodyParameter file: HttpFileUpload): String
 *
 * @HttpPOST(path = "multiFileUpload")
 * fun multiFileUpload(@RestRequestBodyParameter file1: HttpFileUpload,
 *                     @RestRequestBodyParameter file2: HttpFileUpload): String
 *
 * @HttpPOST(path = "fileUploadUsingInputStream")
 * fun fileUploadUsingInputStream(@RestRequestBodyParameter file: InputStream): String
 * ```
 */
class HttpFileUpload(
    val content: InputStream,
    val contentType: String?,
    val extension: String?,
    val fileName: String,
    val size: Long?
) {
    constructor(content: InputStream, fileName: String) : this(content, null, null, fileName, null)
    constructor(content: InputStream, contentType: String, fileName: String) : this(content, contentType, null, fileName, null)
}