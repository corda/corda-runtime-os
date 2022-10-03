package net.cordapp.testing.testflows.messages

class TokenSelectionRequest {
    var tokenType: String? = null
    var issuerHash: String? = null
    var notaryX500Name: String? = null
    var symbol: String? = null
    var targetAmount: Long? = null
    var tagRegex: String? = null
    var ownerHash: String? = null
}
