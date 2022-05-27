package net.cordapp.demo.tictactoe

data class Move(
    val playerX500Name: String,
    val columnPlayed: Int,
    val rowPlayed: Int
)