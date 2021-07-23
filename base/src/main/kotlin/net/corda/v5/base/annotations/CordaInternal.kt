package net.corda.v5.base.annotations

import kotlin.annotation.AnnotationRetention.BINARY
import kotlin.annotation.AnnotationTarget.ANNOTATION_CLASS
import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.annotation.AnnotationTarget.FUNCTION
import kotlin.annotation.AnnotationTarget.PROPERTY
import kotlin.annotation.AnnotationTarget.PROPERTY_GETTER
import kotlin.annotation.AnnotationTarget.PROPERTY_SETTER

/**
 * These methods and annotations are not part of Corda's API compatibility guarantee and applications should not use them.
 *
 * These are only meant to be used by Corda internally, and are not intended to be part of the public API.
 */
@Target(PROPERTY_GETTER, PROPERTY_SETTER, PROPERTY, FUNCTION, ANNOTATION_CLASS, CLASS)
@Retention(BINARY)
@MustBeDocumented
annotation class CordaInternal