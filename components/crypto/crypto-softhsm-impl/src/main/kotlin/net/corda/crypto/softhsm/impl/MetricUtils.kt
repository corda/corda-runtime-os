package net.corda.crypto.softhsm.impl

import net.corda.metrics.CordaMetrics

fun <T : Any> recordGetInstance(type: String, op: () -> T): T {
    return CordaMetrics.Metric.CryptoServiceInstanceCreationTimer.builder()
        .withTag(CordaMetrics.Tag.GetInstanceType, type)
        .build()
        .recordCallable {
            op.invoke()
        }!!
}
