package net.cordacon.example.rollcall.utils

import net.corda.v5.base.types.MemberX500Name

interface ScriptMaker {
    fun createScript(studentResponses: List<Pair<MemberX500Name, String>>, teacher: MemberX500Name): String
}

class BaseScriptMaker : ScriptMaker {
    override fun createScript(
        studentResponses: List<Pair<MemberX500Name, String>>,
        teacher: MemberX500Name
    ) = studentResponses.sortedBy { it.first.rollCallOrder }.joinToString("") {
        val student = it.first.rollCallName
        val teacherPrompt = teacher.rollCallName.uppercase()
        val teacherAsking = "$teacherPrompt: $student?${System.lineSeparator()}"
        val studentResponding =
            if (it.second.isNotEmpty()) {
                student.uppercase() + ": " + it.second + System.lineSeparator()
            } else {
                ""
            }
        teacherAsking + studentResponding
    }
}

val MemberX500Name.rollCallOrder: String
    get() {
        // Put Busch before Bueller when sorted alphabetically
        return if(this.rollCallName =="Busch") "Bua" else this.rollCallName
    }