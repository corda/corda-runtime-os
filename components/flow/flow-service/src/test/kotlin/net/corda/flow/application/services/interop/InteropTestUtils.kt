package net.corda.flow.application.services.interop

import com.fasterxml.jackson.databind.ObjectMapper
import net.corda.flow.application.services.impl.interop.proxies.JacksonJsonMarshaller

/**
 *  JSON marshaller sufficient for unit testing with ObjectMapper
 *  without any additional serializers/deserializers like Merkle trees etc.
 */
val testJsonMarshaller = JacksonJsonMarshaller(ObjectMapper())