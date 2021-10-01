package net.corda.httprpc.annotations

fun Annotation.isHttpRpcParameterAnnotation() =
                this is HttpRpcPathParameter ||
                this is HttpRpcQueryParameter ||
                this is HttpRpcRequestBodyParameter