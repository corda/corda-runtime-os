package net.cordapp.flowworker.development.messages

class TokenSelectionRequest {
    var tokenType: String? = null
    var issuerHash: String? = null
    var notaryHash: String? = null
    var symbol: String? = null
    var targetAmount: Long? = null
    var tagRegex: String? = null
    var ownerHash: String? = null
}