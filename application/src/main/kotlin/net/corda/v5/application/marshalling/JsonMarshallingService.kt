package net.corda.v5.application.marshalling

import net.corda.v5.base.annotations.DoNotImplement

/**
 * [JsonMarshallingService] marshalls to and from JSON using the registered JSON mapper.
 *
 * The platform will provide an instance of [JsonMarshallingService] to flows via property injection.
 */
@DoNotImplement
interface JsonMarshallingService : MarshallingService
