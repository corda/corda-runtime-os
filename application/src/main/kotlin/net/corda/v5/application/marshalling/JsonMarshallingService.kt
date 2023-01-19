package net.corda.v5.application.marshalling

import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.base.annotations.DoNotImplement

/**
 * [JsonMarshallingService] marshalls to and from JSON using the registered JSON mapper.
 *
 * The platform will provide an instance of [JsonMarshallingService] to flows via property injection.
 *
 * Example usage:
 * @see ClientStartableFlow
 */
@DoNotImplement
interface JsonMarshallingService : MarshallingService
