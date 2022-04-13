package net.corda.v5.application.persistence.query

import java.util.stream.Stream

/**
 * Post-processors can be implemented to enhance the Named Query API.
 *
 * [Stream]s are used for lazy operations to reduce memory footprint.
 *
 * This interface allows implementors to operate on query results of type [Any]. It is up to the implementor to perform type checking and
 * ensure type safety during casting of the query result. Returns a [Stream] of user defined type [R].
 *
 * [name] is used to identify post-processors during the Named Query RPC API.
 */
interface CustomQueryPostProcessor<R> : GenericQueryPostProcessor<Any?, R>