package net.cordapp.flowworker.development.messages

data class TokenSelectionResponse (
     val resultType: String,
     val tokenAmounts: List<Long>
 )