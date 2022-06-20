package net.corda.v5.application.marshalling

import net.corda.v5.base.annotations.DoNotImplement

/**
 * An optional service CorDapps and other services may use to marshall arbitrary content in and out of JSON format using standard/approved
 * mappers.
 */
@DoNotImplement
interface JsonMarshallingService : MarshallingService