package net.corda.linearstatesample.flows

import net.corda.v5.application.flows.Flow
import net.corda.v5.application.flows.InitiatingFlow

import net.corda.v5.application.flows.StartableByRPC

import net.corda.v5.base.annotations.Suspendable

object HelloWorldFlow {
    @InitiatingFlow
    @StartableByRPC
    class Initiator : Flow<Boolean> {

        @Suspendable
        override fun call() : Boolean {
            println("I am a FLOW!!!!!!!!!!!!!!!!")
            return true
        }
    }
}