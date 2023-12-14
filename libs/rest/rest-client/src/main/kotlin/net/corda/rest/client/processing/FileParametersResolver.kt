package net.corda.rest.client.processing

import net.corda.rest.HttpFileUpload
import net.corda.rest.annotations.ClientRequestBodyParameter
import net.corda.rest.tools.annotations.extensions.name
import net.corda.utilities.trace
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.lang.reflect.Method
import java.lang.reflect.Parameter

private val log = LoggerFactory.getLogger("net.corda.rest.client.processing.FileParametersResolver.kt")

internal fun Method.filesFrom(methodArguments: Array<out Any?>): Map<String, List<RestClientFileUpload>> {
    log.trace { """Extracting files from method arguments "$methodArguments".""" }

    val inputStreamsByName = extractInputStreams(methodArguments)
    val filesByName = extractHttpFileUploads(methodArguments)
    val fileListsByName = extractHttpFileUploadLists(methodArguments)

    return inputStreamsByName + filesByName + fileListsByName
        .also { log.trace { """Extracting files from "$methodArguments" completed.""" } }
}

private fun Method.extractInputStreams(methodArguments: Array<out Any?>): Map<String, List<RestClientFileUpload>> {
    val inputStreamsToUpload = this.parameters
        .mapIndexed { index, parameter -> parameter to methodArguments[index] }
        .filter { it.first.type == InputStream::class.java }

    val inputStreamsPerFieldName = inputStreamsToUpload.associate {
        val fieldName = getFieldNameFromAnnotationOrParameter(it)
        val content = it.second as InputStream
        // Unirest client requires file uploads have a filename, so we set it to the name of parameter.
        fieldName to listOf(RestClientFileUpload(content, fieldName))
    }
    return inputStreamsPerFieldName
}

private fun Method.extractHttpFileUploads(methodArguments: Array<out Any?>): Map<String, List<RestClientFileUpload>> {
    val filesToUpload = this.parameters
        .mapIndexed { index, parameter -> parameter to methodArguments[index] }
        .filter { it.first.type == HttpFileUpload::class.java }

    val filesPerFieldName = filesToUpload.associate {
        val fieldName = getFieldNameFromAnnotationOrParameter(it)
        val content = it.second as HttpFileUpload
        fieldName to listOf(RestClientFileUpload(content.content, content.fileName))
    }
    return filesPerFieldName
}

private fun Method.extractHttpFileUploadLists(methodArguments: Array<out Any?>): Map<String, List<RestClientFileUpload>> {
    val fileListsToUpload = this.parameters
        .mapIndexed { index, parameter -> parameter to methodArguments[index] }
        .filter { isParameterAListOfFiles(it.first) }

    val fileListPerFieldName = fileListsToUpload.associate {
        val fieldName = getFieldNameFromAnnotationOrParameter(it)
        val content = it.second as Collection<*>
        val fileUploads = content.map { c ->
            val file = c as HttpFileUpload
            RestClientFileUpload(file.content, file.fileName)
        }
        fieldName to fileUploads
    }
    return fileListPerFieldName
}

private fun getFieldNameFromAnnotationOrParameter(it: Pair<Parameter, Any?>): String {
    val name = if (it.first.annotations.any { annotation -> annotation is ClientRequestBodyParameter }) {
        it.first.getAnnotation(ClientRequestBodyParameter::class.java).name(it.first)
    } else {
        it.first.name
    }
    return name
}
