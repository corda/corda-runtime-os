package net.corda.tracing.impl

import brave.baggage.BaggageField

object BraveBaggageFields {
    val REQUEST_ID: BaggageField = BaggageField.create("request_id")
    val VIRTUAL_NODE_ID: BaggageField = BaggageField.create("vnode_id")
    val TRANSACTION_ID: BaggageField = BaggageField.create("tx_id")
}