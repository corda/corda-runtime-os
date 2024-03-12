package net.corda.tracing.brave

import brave.baggage.BaggageField

object BraveBaggageFields {
    val REQUEST_ID: BaggageField = BaggageField.create("request.id")
    val VIRTUAL_NODE_ID: BaggageField = BaggageField.create("vnode.id")
}