package net.corda.ledger.utxo.token.cache.entities

interface TokenFilter : TokenEvent {

    /**
     * @return Returns the regular expression for a tag which the tokens will be matched against.
     */
    val tagRegex: String?

    /**
     * @return Returns the owner's hash which the tokens will be match against.
     */
    val ownerHash: String?
}
