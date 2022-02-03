package net.corda.session.mapper.service

/**
 * Exception type for the flow mapper service.
 */
class FlowMapperException(msg: String, cause: Throwable? = null) : RuntimeException(msg, cause)