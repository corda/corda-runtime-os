package net.corda.v5.base.annotations

/**
 * Marks a method as suspendable.
 *
 * This annotation is required to allow a fiber (a special lightweight thread used by flows in Corda) to suspend and release the underlying
 * thread to another fiber.
 */
@kotlin.annotation.Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER,
    AnnotationTarget.PROPERTY,
)
@kotlin.annotation.Retention
@MustBeDocumented
annotation class Suspendable