package net.corda.ledger.persistence.query.parsing

interface Token

object LeftParenthesis : Token

object RightParenthesis : Token

object ParameterEnd : Token
