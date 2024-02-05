package net.corda.e2etest.utilities.externaldb

import java.security.SecureRandom

class PasswordGenerator {
    private val passwordLength = 64
    private val passwordSource = (('0'..'9') + ('A'..'Z') + ('a'..'z')).toCharArray()
    private val random = SecureRandom()

    fun generatePassword(): CharArray {
        val password = CharArray(passwordLength)
        for (i in 0 until passwordLength) password[i] = passwordSource[random.nextInt(passwordSource.size)]
        return password
    }
}
