package net.corda.httprpc.annotations

fun Annotation.isHttpRpcParameterAnnotation() =
    this is RestPathParameter ||
            this is RestQueryParameter ||
            this is ClientRequestBodyParameter