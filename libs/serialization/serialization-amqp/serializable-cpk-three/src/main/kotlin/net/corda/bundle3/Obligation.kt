package net.corda.bundle3

import net.corda.bundle1.Cash
import net.corda.bundle2.Document
import net.corda.v5.base.annotations.CordaSerializable

@CordaSerializable
class Obligation(val amount: Cash) {

    /**
     * This toString method uses the Document class from the same bundle.
     * This Document class is different from the Document class from TestSerializable2.
     * Both have the same fully qualified class name.
     */
    override fun toString(): String {

        val document = Document("This is some string", 1)

        return "with (amount: ${amount.amount}, " +
                "content: ${document.content}, version: ${document.version}"

    }
}