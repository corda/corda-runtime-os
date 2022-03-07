package net.corda.demo.mandelbrot

class RequestMessage {
    var startX: Double? = null
    var startY: Double? = null
    var iterationMax: Int = 1000
    var width: Double? = null
    var height: Double? = null
    var pixelWidth: Int = 32
    var pixelHeight: Int = 32
}

