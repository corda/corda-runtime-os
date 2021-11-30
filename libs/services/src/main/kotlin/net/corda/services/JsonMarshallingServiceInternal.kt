package net.corda.services

import net.corda.v5.application.services.json.JsonMarshallingService
import net.corda.v5.serialization.SingletonSerializeAsToken

@Suppress("EmptyClassBlock")
interface JsonMarshallingServiceInternal: JsonMarshallingService, SingletonSerializeAsToken {
}