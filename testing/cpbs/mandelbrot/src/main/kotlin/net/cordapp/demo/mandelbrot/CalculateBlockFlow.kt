package net.cordapp.demo.mandelbrot

import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.RestRequestBody
import net.corda.v5.application.flows.getRequestBodyAs
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.Suspendable
import org.slf4j.LoggerFactory

class CalculateBlockFlow : ClientStartableFlow {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)

        val colourPalette = Array(255) { IntArray(3) { 0 } }.apply {
            this[0] = intArrayOf(0, 0, 0)
            this[1] = intArrayOf(255, 235, 238)
            this[2] = intArrayOf(255, 205, 210)
            this[3] = intArrayOf(239, 154, 154)
            this[4] = intArrayOf(229, 115, 115)
            this[5] = intArrayOf(239, 83, 80)
            this[6] = intArrayOf(244, 67, 54)
            this[7] = intArrayOf(229, 57, 53)
            this[8] = intArrayOf(211, 47, 47)
            this[9] = intArrayOf(198, 40, 40)
            this[10] = intArrayOf(183, 28, 28)
            this[11] = intArrayOf(255, 138, 128)
            this[12] = intArrayOf(255, 82, 82)
            this[13] = intArrayOf(255, 23, 68)
            this[14] = intArrayOf(213, 0, 0)
            this[15] = intArrayOf(252, 228, 236)
            this[16] = intArrayOf(248, 187, 208)
            this[17] = intArrayOf(244, 143, 177)
            this[18] = intArrayOf(240, 98, 146)
            this[19] = intArrayOf(236, 64, 122)
            this[20] = intArrayOf(233, 30, 99)
            this[21] = intArrayOf(216, 27, 96)
            this[22] = intArrayOf(194, 24, 91)
            this[23] = intArrayOf(173, 20, 87)
            this[24] = intArrayOf(136, 14, 79)
            this[25] = intArrayOf(255, 128, 171)
            this[26] = intArrayOf(255, 64, 129)
            this[27] = intArrayOf(245, 0, 87)
            this[28] = intArrayOf(197, 17, 98)
            this[29] = intArrayOf(243, 229, 245)
            this[30] = intArrayOf(225, 190, 231)
            this[31] = intArrayOf(206, 147, 216)
            this[32] = intArrayOf(186, 104, 200)
            this[33] = intArrayOf(171, 71, 188)
            this[34] = intArrayOf(156, 39, 176)
            this[35] = intArrayOf(142, 36, 170)
            this[36] = intArrayOf(123, 31, 162)
            this[37] = intArrayOf(106, 27, 154)
            this[38] = intArrayOf(74, 20, 140)
            this[39] = intArrayOf(234, 128, 252)
            this[40] = intArrayOf(224, 64, 251)
            this[41] = intArrayOf(213, 0, 249)
            this[42] = intArrayOf(170, 0, 255)
            this[43] = intArrayOf(237, 231, 246)
            this[44] = intArrayOf(209, 196, 233)
            this[45] = intArrayOf(179, 157, 219)
            this[46] = intArrayOf(149, 117, 205)
            this[47] = intArrayOf(126, 87, 194)
            this[48] = intArrayOf(103, 58, 183)
            this[49] = intArrayOf(94, 53, 177)
            this[50] = intArrayOf(81, 45, 168)
            this[51] = intArrayOf(69, 39, 160)
            this[52] = intArrayOf(49, 27, 146)
            this[53] = intArrayOf(179, 136, 255)
            this[54] = intArrayOf(124, 77, 255)
            this[55] = intArrayOf(101, 31, 255)
            this[56] = intArrayOf(98, 0, 234)
            this[57] = intArrayOf(232, 234, 246)
            this[58] = intArrayOf(197, 202, 233)
            this[59] = intArrayOf(159, 168, 218)
            this[60] = intArrayOf(121, 134, 203)
            this[61] = intArrayOf(92, 107, 192)
            this[62] = intArrayOf(63, 81, 181)
            this[63] = intArrayOf(57, 73, 171)
            this[64] = intArrayOf(48, 63, 159)
            this[65] = intArrayOf(40, 53, 147)
            this[66] = intArrayOf(26, 35, 126)
            this[67] = intArrayOf(140, 158, 255)
            this[68] = intArrayOf(83, 109, 254)
            this[69] = intArrayOf(61, 90, 254)
            this[70] = intArrayOf(48, 79, 254)
            this[71] = intArrayOf(227, 242, 253)
            this[72] = intArrayOf(187, 222, 251)
            this[73] = intArrayOf(144, 202, 249)
            this[74] = intArrayOf(100, 181, 246)
            this[75] = intArrayOf(66, 165, 245)
            this[76] = intArrayOf(33, 150, 243)
            this[77] = intArrayOf(30, 136, 229)
            this[78] = intArrayOf(25, 118, 210)
            this[79] = intArrayOf(21, 101, 192)
            this[80] = intArrayOf(13, 71, 161)
            this[81] = intArrayOf(130, 177, 255)
            this[82] = intArrayOf(68, 138, 255)
            this[83] = intArrayOf(41, 121, 255)
            this[84] = intArrayOf(41, 98, 255)
            this[85] = intArrayOf(225, 245, 254)
            this[86] = intArrayOf(179, 229, 252)
            this[87] = intArrayOf(129, 212, 250)
            this[88] = intArrayOf(79, 195, 247)
            this[89] = intArrayOf(41, 182, 246)
            this[90] = intArrayOf(3, 169, 244)
            this[91] = intArrayOf(3, 155, 229)
            this[92] = intArrayOf(2, 136, 209)
            this[93] = intArrayOf(2, 119, 189)
            this[94] = intArrayOf(1, 87, 155)
            this[95] = intArrayOf(128, 216, 255)
            this[96] = intArrayOf(64, 196, 255)
            this[97] = intArrayOf(0, 176, 255)
            this[98] = intArrayOf(0, 145, 234)
            this[99] = intArrayOf(224, 247, 250)
            this[100] = intArrayOf(178, 235, 242)
            this[101] = intArrayOf(128, 222, 234)
            this[102] = intArrayOf(77, 208, 225)
            this[103] = intArrayOf(38, 198, 218)
            this[104] = intArrayOf(0, 188, 212)
            this[105] = intArrayOf(0, 172, 193)
            this[106] = intArrayOf(0, 151, 167)
            this[107] = intArrayOf(0, 131, 143)
            this[108] = intArrayOf(0, 96, 100)
            this[109] = intArrayOf(132, 255, 255)
            this[110] = intArrayOf(24, 255, 255)
            this[111] = intArrayOf(0, 229, 255)
            this[112] = intArrayOf(0, 184, 212)
            this[113] = intArrayOf(224, 242, 241)
            this[114] = intArrayOf(178, 223, 219)
            this[115] = intArrayOf(128, 203, 196)
            this[116] = intArrayOf(77, 182, 172)
            this[117] = intArrayOf(38, 166, 154)
            this[118] = intArrayOf(0, 150, 136)
            this[119] = intArrayOf(0, 137, 123)
            this[120] = intArrayOf(0, 121, 107)
            this[121] = intArrayOf(0, 105, 92)
            this[122] = intArrayOf(0, 77, 64)
            this[123] = intArrayOf(167, 255, 235)
            this[124] = intArrayOf(100, 255, 218)
            this[125] = intArrayOf(29, 233, 182)
            this[126] = intArrayOf(0, 191, 165)
            this[127] = intArrayOf(232, 245, 233)
            this[128] = intArrayOf(200, 230, 201)
            this[129] = intArrayOf(165, 214, 167)
            this[130] = intArrayOf(129, 199, 132)
            this[131] = intArrayOf(102, 187, 106)
            this[132] = intArrayOf(76, 175, 80)
            this[133] = intArrayOf(67, 160, 71)
            this[134] = intArrayOf(56, 142, 60)
            this[135] = intArrayOf(46, 125, 50)
            this[136] = intArrayOf(27, 94, 32)
            this[137] = intArrayOf(185, 246, 202)
            this[138] = intArrayOf(105, 240, 174)
            this[139] = intArrayOf(0, 230, 118)
            this[140] = intArrayOf(0, 200, 83)
            this[141] = intArrayOf(241, 248, 233)
            this[142] = intArrayOf(220, 237, 200)
            this[143] = intArrayOf(197, 225, 165)
            this[144] = intArrayOf(174, 213, 129)
            this[145] = intArrayOf(156, 204, 101)
            this[146] = intArrayOf(139, 195, 74)
            this[147] = intArrayOf(124, 179, 66)
            this[148] = intArrayOf(104, 159, 56)
            this[149] = intArrayOf(85, 139, 47)
            this[150] = intArrayOf(51, 105, 30)
            this[151] = intArrayOf(204, 255, 144)
            this[152] = intArrayOf(178, 255, 89)
            this[153] = intArrayOf(118, 255, 3)
            this[154] = intArrayOf(100, 221, 23)
            this[155] = intArrayOf(249, 251, 231)
            this[156] = intArrayOf(240, 244, 195)
            this[157] = intArrayOf(230, 238, 156)
            this[158] = intArrayOf(220, 231, 117)
            this[159] = intArrayOf(212, 225, 87)
            this[160] = intArrayOf(205, 220, 57)
            this[161] = intArrayOf(192, 202, 51)
            this[162] = intArrayOf(175, 180, 43)
            this[163] = intArrayOf(158, 157, 36)
            this[164] = intArrayOf(130, 119, 23)
            this[165] = intArrayOf(244, 255, 129)
            this[166] = intArrayOf(238, 255, 65)
            this[167] = intArrayOf(198, 255, 0)
            this[168] = intArrayOf(174, 234, 0)
            this[169] = intArrayOf(255, 253, 231)
            this[170] = intArrayOf(255, 249, 196)
            this[171] = intArrayOf(255, 245, 157)
            this[172] = intArrayOf(255, 241, 118)
            this[173] = intArrayOf(255, 238, 88)
            this[174] = intArrayOf(255, 235, 59)
            this[175] = intArrayOf(253, 216, 53)
            this[176] = intArrayOf(251, 192, 45)
            this[177] = intArrayOf(249, 168, 37)
            this[178] = intArrayOf(245, 127, 23)
            this[179] = intArrayOf(255, 255, 141)
            this[180] = intArrayOf(255, 255, 0)
            this[181] = intArrayOf(255, 234, 0)
            this[182] = intArrayOf(255, 214, 0)
            this[183] = intArrayOf(255, 248, 225)
            this[184] = intArrayOf(255, 236, 179)
            this[185] = intArrayOf(255, 224, 130)
            this[186] = intArrayOf(255, 213, 79)
            this[187] = intArrayOf(255, 202, 40)
            this[188] = intArrayOf(255, 193, 7)
            this[189] = intArrayOf(255, 179, 0)
            this[190] = intArrayOf(255, 160, 0)
            this[191] = intArrayOf(255, 143, 0)
            this[192] = intArrayOf(255, 111, 0)
            this[193] = intArrayOf(255, 229, 127)
            this[194] = intArrayOf(255, 215, 64)
            this[195] = intArrayOf(255, 196, 0)
            this[196] = intArrayOf(255, 171, 0)
            this[197] = intArrayOf(255, 243, 224)
            this[198] = intArrayOf(255, 224, 178)
            this[199] = intArrayOf(255, 204, 128)
            this[200] = intArrayOf(255, 183, 77)
            this[201] = intArrayOf(255, 167, 38)
            this[202] = intArrayOf(255, 152, 0)
            this[203] = intArrayOf(251, 140, 0)
            this[204] = intArrayOf(245, 124, 0)
            this[205] = intArrayOf(239, 108, 0)
            this[206] = intArrayOf(230, 81, 0)
            this[207] = intArrayOf(255, 209, 128)
            this[208] = intArrayOf(255, 171, 64)
            this[209] = intArrayOf(255, 145, 0)
            this[210] = intArrayOf(255, 109, 0)
            this[211] = intArrayOf(251, 233, 231)
            this[212] = intArrayOf(255, 204, 188)
            this[213] = intArrayOf(255, 171, 145)
            this[214] = intArrayOf(255, 138, 101)
            this[215] = intArrayOf(255, 112, 67)
            this[216] = intArrayOf(255, 87, 34)
            this[217] = intArrayOf(244, 81, 30)
            this[218] = intArrayOf(230, 74, 25)
            this[219] = intArrayOf(216, 67, 21)
            this[220] = intArrayOf(191, 54, 12)
            this[221] = intArrayOf(255, 158, 128)
            this[222] = intArrayOf(255, 110, 64)
            this[223] = intArrayOf(255, 61, 0)
            this[224] = intArrayOf(221, 44, 0)
            this[225] = intArrayOf(239, 235, 233)
            this[226] = intArrayOf(215, 204, 200)
            this[227] = intArrayOf(188, 170, 164)
            this[228] = intArrayOf(161, 136, 127)
            this[229] = intArrayOf(141, 110, 99)
            this[230] = intArrayOf(121, 85, 72)
            this[231] = intArrayOf(109, 76, 65)
            this[232] = intArrayOf(93, 64, 55)
            this[233] = intArrayOf(78, 52, 46)
            this[234] = intArrayOf(62, 39, 35)
            this[235] = intArrayOf(250, 250, 250)
            this[236] = intArrayOf(245, 245, 245)
            this[237] = intArrayOf(238, 238, 238)
            this[238] = intArrayOf(224, 224, 224)
            this[239] = intArrayOf(189, 189, 189)
            this[240] = intArrayOf(158, 158, 158)
            this[241] = intArrayOf(117, 117, 117)
            this[242] = intArrayOf(97, 97, 97)
            this[243] = intArrayOf(66, 66, 66)
            this[244] = intArrayOf(33, 33, 33)
            this[245] = intArrayOf(236, 239, 241)
            this[246] = intArrayOf(207, 216, 220)
            this[247] = intArrayOf(176, 190, 197)
            this[248] = intArrayOf(144, 164, 174)
            this[249] = intArrayOf(120, 144, 156)
            this[250] = intArrayOf(96, 125, 139)
            this[251] = intArrayOf(84, 110, 122)
            this[252] = intArrayOf(69, 90, 100)
            this[253] = intArrayOf(55, 71, 79)
            this[254] = intArrayOf(38, 50, 56)
        }
    }

    @CordaInject
    private lateinit var jsonMarshallingService: JsonMarshallingService

    @Suppress("NestedBlockDepth")
    @Suspendable
    override fun call(requestBody: RestRequestBody): String {
        log.info("Starting mandelbrot calc...")

        try {
            val requestMessage = requestBody.getRequestBodyAs<RequestMessage>(jsonMarshallingService)

            val response = Array(requestMessage.pixelWidth * requestMessage.pixelHeight) { IntArray(3) { 0 } }

            val iterationMax = requestMessage.iterationMax
            val pixelWidth = requestMessage.pixelWidth
            val pixelHeight = requestMessage.pixelHeight
            val xscale = requestMessage.width!! / requestMessage.pixelWidth
            val yscale = requestMessage.height!! / requestMessage.pixelHeight
            val xOffset = requestMessage.startX!!
            val yOffset = requestMessage.startY!!

            var sy = 0
            while (sy < pixelHeight) {
                var sx = 0
                while (sx < pixelWidth) {
                    var i = 0
                    var x = 0.0
                    var y = 0.0

                    val cx = xOffset + (sx * xscale)
                    val cy = yOffset + (sy * yscale)

                    while (x * x + y * y < 4 && i < iterationMax) {
                        val t = x * x - y * y + cx
                        y = 2 * x * y + cy
                        x = t
                        i++
                    }

                    if(i==iterationMax){
                        i=0
                    }
                    val idx = sx + (sy * pixelWidth)
                    response[idx] =  colourPalette[i % colourPalette.size]

                    sx++
                }
                sy++
            }

            return jsonMarshallingService.format(response)

        } catch (e: Exception) {
            log.error("Failed to calculate mandelbrot '$requestBody' because '${e.message}'")
            throw e
        }
    }
}

