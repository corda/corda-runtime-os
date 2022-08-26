package net.corda.v5.base.annotations

/**
 * Marks a method as suspendable.
 *
 * This annotation is required to allow a fiber (a special lightweight thread used by flows in Corda) to suspend and release the underlying
 * thread to another fiber.
 */
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER,
    AnnotationTarget.PROPERTY,
)
@Retention
@MustBeDocumented
annotation class Suspendable
