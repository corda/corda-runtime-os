package net.corda.v5.httprpc.api.annotations

fun Annotation.isHttpRpcParameterAnnotation() =
                this is HttpRpcPathParameter ||
                this is HttpRpcQueryParameter ||
                this is HttpRpcRequestBodyParameter