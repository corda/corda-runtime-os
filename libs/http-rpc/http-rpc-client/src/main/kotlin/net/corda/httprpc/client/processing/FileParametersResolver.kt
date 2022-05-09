package net.corda.httprpc.client.processing

import java.io.InputStream
import net.corda.httprpc.annotations.HttpRpcRequestBodyParameter
import net.corda.httprpc.tools.annotations.extensions.name
import net.corda.v5.base.util.trace
import org.slf4j.LoggerFactory
import java.lang.reflect.Method
import net.corda.httprpc.HttpFileUpload

private val log = LoggerFactory.getLogger("net.corda.httprpc.client.internal.processing.BodyParametersResolver.kt")

internal fun Method.filesFrom(methodArguments: Array<out Any?>): Map<String, HttpRpcClientFileUpload> {
    log.trace { """Extracting files from method arguments "$methodArguments".""" }

    val inputStreamsToUpload = this.parameters
        .mapIndexed { index, parameter -> parameter to methodArguments[index] }
        .filter {
            it.first.type == InputStream::class.java
        }

    // we will set the filename of the inputstream to default to the name of the parameter
    val inputStreamsByName = inputStreamsToUpload.associate {
        val name = if (it.first.annotations.any { annotation -> annotation is HttpRpcRequestBodyParameter }) {
            it.first.getAnnotation(HttpRpcRequestBodyParameter::class.java).name(it.first)
        } else {
            it.first.name
        }
        val content = it.second as InputStream
        name to HttpRpcClientFileUpload(content, name)
    }

    val filesToUpload = this.parameters
        .mapIndexed { index, parameter -> parameter to methodArguments[index] }
        .filter { it.first.type == HttpFileUpload::class.java }

    // actual files being uploaded will preserve their actual filename as metadata
    val filesByName = filesToUpload.associate {
        val name = if (it.first.annotations.any { annotation -> annotation is HttpRpcRequestBodyParameter }) {
            it.first.getAnnotation(HttpRpcRequestBodyParameter::class.java).name(it.first)
        } else {
            it.first.name
        }
        val content = it.second as HttpFileUpload
        name to HttpRpcClientFileUpload(content.content, content.fileName)
    }

    return inputStreamsByName + filesByName
        .also { log.trace { """Extracting files from "$methodArguments" completed.""" } }
}