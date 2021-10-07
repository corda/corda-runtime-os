package net.corda.v5.membership.identity.internal

import java.text.Normalizer
import javax.security.auth.x500.X500Principal

internal object LegalNameValidator {

    /**
     * The validation function validates a string for use as the organization attribute of a name, which includes additional
     * constraints over basic name attribute checks. It applies the following additional rules:
     *
     * - Must be normalized (as per the [normalize] function).
     * - Length must be 255 characters or shorter.
     * - No blacklisted words like "node", "server".
     * - Must consist of at least three letters.
     *
     * Full validation (typically this is only done for names the Doorman approves) adds:
     *
     * - Restrict names to Latin scripts for now to avoid right-to-left issues, debugging issues when we can't pronounce
     *   names over the phone, and character confusability attacks.
     * - Must start with a capital letter.
     * - No commas or equals signs.
     * - No dollars or quote marks, we might need to relax the quote mark constraint in future to handle Irish company names.
     *
     * @throws IllegalArgumentException if the name does not meet the required rules. The message indicates why not.
     */
    fun validateOrganization(normalizedOrganization: String) {
        Rule.legalNameRules.forEach { it.validate(normalizedOrganization) }
    }

    /**
     * The normalize function will trim the input string, replace any multiple spaces with a single space,
     * and normalize the string according to NFKC normalization form.
     */
    fun normalize(nameAttribute: String): String {
        val trimmedLegalName = nameAttribute.trim().replace(WHITESPACE, " ")
        return Normalizer.normalize(trimmedLegalName, Normalizer.Form.NFKC)
    }

    private val WHITESPACE = "\\s++".toRegex()

    sealed class Rule<in T> {
        companion object {
            val attributeRules: List<Rule<String>> = listOf(
                UnicodeNormalizationRule(),
                LengthRule(maxLength = 255),
                MustHaveAtLeastTwoLettersRule(),
                CharacterRule('\u0000') // Ban null
            )
            val legalNameRules: List<Rule<String>> = attributeRules + listOf(X500NameRule())
        }

        abstract fun validate(legalName: T)

        private class UnicodeNormalizationRule : Rule<String>() {
            override fun validate(legalName: String) {
                require(legalName == normalize(legalName)) { "Legal name must be normalized. Please use 'normalize' to normalize the legal name before validation." }
            }
        }

        private class CharacterRule(vararg val bannedChars: Char) : Rule<String>() {
            override fun validate(legalName: String) {
                bannedChars.forEach {
                    require(!legalName.contains(it, true)) { "Character not allowed in legal names: $it" }
                }
            }
        }

        private class LengthRule(val maxLength: Int) : Rule<String>() {
            override fun validate(legalName: String) {
                require(legalName.length <= maxLength) { "Legal name longer then $maxLength characters." }
            }
        }

        private class X500NameRule : Rule<String>() {
            override fun validate(legalName: String) {
                // This will throw IllegalArgumentException if the name does not comply with X500 name format.
                X500Principal("CN=$legalName")
            }
        }

        private class MustHaveAtLeastTwoLettersRule : Rule<String>() {
            override fun validate(legalName: String) {
                // Try to exclude names like "/", "£", "X" etc.
                require(legalName.count { it.isLetter() } >= 2) { "Illegal input legal name '$legalName'. Legal name must have at least two letters" }
            }
        }
    }
}