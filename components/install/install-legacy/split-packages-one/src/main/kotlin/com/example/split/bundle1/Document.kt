package com.example.split.bundle1

import com.example.split.library.Widget1

@Suppress("unused")
data class Document(val content: String) {
    override fun toString(): String {
        return "${super.toString()} - ${Widget1(content)}"
    }
}
