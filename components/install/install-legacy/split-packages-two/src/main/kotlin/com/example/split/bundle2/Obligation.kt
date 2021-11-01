package com.example.split.bundle2

import com.example.split.bundle1.Document
import com.example.split.library.Widget2

@Suppress("unused")
class Obligation(private val amount: Long) {
    override fun toString(): String {
        val document = Document("This is some string", 1)
        return "with (amount: ${amount}, " +
               "content: ${document.content}, version: ${document.version}, " +
               "widget: ${Widget2(amount)}"
    }
}
