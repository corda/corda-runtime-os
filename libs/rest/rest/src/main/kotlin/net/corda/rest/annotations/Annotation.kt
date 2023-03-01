package net.corda.rest.annotations

fun Annotation.isRestParameterAnnotation() =
    this is RestPathParameter ||
            this is RestQueryParameter ||
            this is ClientRequestBodyParameter