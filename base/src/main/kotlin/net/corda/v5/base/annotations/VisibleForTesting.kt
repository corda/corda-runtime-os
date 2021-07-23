package net.corda.v5.base.annotations

/** The annotated object would have a more restricted visibility were it not needed in tests. */
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.TYPEALIAS
)
@Retention(AnnotationRetention.SOURCE)
@MustBeDocumented
annotation class VisibleForTesting