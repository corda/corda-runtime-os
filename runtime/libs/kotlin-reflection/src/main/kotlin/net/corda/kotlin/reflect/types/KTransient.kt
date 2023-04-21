package net.corda.kotlin.reflect.types

/**
 * Marker interface for objects which are not
 * inherited from super types, even if they
 * are otherwise inheritable (i.e. "public").
 */
interface KTransient
