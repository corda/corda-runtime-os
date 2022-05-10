package net.corda.httprpc.client.processing

import java.io.InputStream
import net.corda.httprpc.annotations.HttpRpcRequestBodyParameter
import net.corda.httprpc.tools.annotations.extensions.name
import net.corda.v5.base.util.trace
import org.slf4j.LoggerFactory
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import net.corda.httprpc.HttpFileUpload

private val log = LoggerFactory.getLogger("net.corda.httprpc.client.internal.processing.FileParametersResolver.kt")

internal fun Method.filesFrom(methodArguments: Array<out Any?>): Map<String, List<HttpRpcClientFileUpload>> {
    log.trace { """Extracting files from method arguments "$methodArguments".""" }

    val inputStreamsByName = extractInputStreams(methodArguments)

    val filesByName = extractHttpFileUploads(methodArguments)

    val fileListsByName = extractHttpFileUploadLists(methodArguments)

    return inputStreamsByName + filesByName + fileListsByName
        .also { log.trace { """Extracting files from "$methodArguments" completed.""" } }
}

private fun Method.extractHttpFileUploadLists(methodArguments: Array<out Any?>): Map<String, List<HttpRpcClientFileUpload>> {
    val fileListsToUpload = this.parameters
        .mapIndexed { index, parameter -> parameter to methodArguments[index] }
        .filter {
            if (it.first.parameterizedType is ParameterizedType && Collection::class.java.isAssignableFrom(it.first.type)) {
                val type = it.first.parameterizedType as ParameterizedType
                type.actualTypeArguments.size == 1 && type.actualTypeArguments.first() == HttpFileUpload::class.java
            } else {
                false
            }
        }

    val fileListsByName = fileListsToUpload.associate {
        val name = if (it.first.annotations.any { annotation -> annotation is HttpRpcRequestBodyParameter }) {
            it.first.getAnnotation(HttpRpcRequestBodyParameter::class.java).name(it.first)
        } else {
            it.first.name
        }
        val content = it.second as Collection<*>
        val fileUploads = content.map { c ->
            val file = c as HttpFileUpload
            HttpRpcClientFileUpload(file.content, file.fileName)
        }
        name to fileUploads
    }
    return fileListsByName
}

private fun Method.extractHttpFileUploads(methodArguments: Array<out Any?>): Map<String, List<HttpRpcClientFileUpload>> {
    val filesToUpload = this.parameters
        .mapIndexed { index, parameter -> parameter to methodArguments[index] }
        .filter { it.first.type == HttpFileUpload::class.java }

    val filesByName = filesToUpload.associate {
        val name = if (it.first.annotations.any { annotation -> annotation is HttpRpcRequestBodyParameter }) {
            it.first.getAnnotation(HttpRpcRequestBodyParameter::class.java).name(it.first)
        } else {
            it.first.name
        }
        val content = it.second as HttpFileUpload
        name to listOf(HttpRpcClientFileUpload(content.content, content.fileName))
    }
    return filesByName
}

private fun Method.extractInputStreams(methodArguments: Array<out Any?>): Map<String, List<HttpRpcClientFileUpload>> {
    val inputStreamsToUpload = this.parameters
        .mapIndexed { index, parameter -> parameter to methodArguments[index] }
        .filter {
            it.first.type == InputStream::class.java
        }

    val inputStreamsByName = inputStreamsToUpload.associate {
        val name = if (it.first.annotations.any { annotation -> annotation is HttpRpcRequestBodyParameter }) {
            it.first.getAnnotation(HttpRpcRequestBodyParameter::class.java).name(it.first)
        } else {
            it.first.name
        }
        val content = it.second as InputStream
        // Unirest client requires file uploads have a filename, so we set it to the name of parameter.
        name to listOf(HttpRpcClientFileUpload(content, name))
    }
    return inputStreamsByName
}