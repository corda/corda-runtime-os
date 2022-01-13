package net.corda.datasync

import net.corda.datasync.MurmurHash3.LongPair
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.experimental.and

class MurmurHash3Test {
    companion object {
        private val answers32 = intArrayOf(
            0x11fd02eb,
            -0x7229a58d,
            0x29b074ba,
            -0x3432bc32,
            -0x49b9c77f,
            -0x9dd7aa9,
            0x3d55c634,
            -0x5e446f8e,
            0x448402c6,
            -0x4ed40c2c,
            0x18a71ccb,
            0x6ae5f185,
            -0x65b7ddaa,
            -0x3979280e,
            -0x71767b28,
            0x68a2491d,
            -0x33d64f1a,
            0x3e9130bd,
            -0x36f2104d,
            -0x7e3a688,
            0x15ff7f63,
            0x4ec16a7a,
            -0x5f755767,
            0x7317ffee,
            -0x6c8ad2cc,
            0x400f8781,
            0x2358838c,
            0x6ecb8998,
            0x45a5c102,
            0x46ed68fd,
            -0x134ae40,
            0x7a68c7db,
            -0x61ccb155,
            0x21ea13b6,
            -0xe7b16d4,
            -0x3fe9ddf3,
            0x7f6c9713,
            0x1e909123,
            -0x4ae5de49,
            -0x6b3a777f,
            -0x1b16e410,
            -0x217f5c9a,
            -0x27bffa6,
            0x3361d373,
            -0x182ad734,
            0x487275a7,
            -0xdd6f11b,
            -0x79666d58,
            0x63cdd341,
            -0x716b4ccc,
            0x1fc7bf11,
            0x5228b0,
            -0x4bd6d49e,
            0x36ed3770,
            -0x16ebae7,
            0x7d9d1830,
            -0x1e5304a0,
            -0x374b2b49,
            -0xe13b646,
            -0x1244733f,
            -0x23a4c54f,
            0x7c7778ae,
            0x52bf68d,
            -0x1f44beb8,
            -0x15c9adf,
            -0x5f96935b,
            -0xd7208ae,
            -0x27d2334a
        )

        private val answers128 = longArrayOf(
            0x6e54d3ad2be8e9a2L,
            -0x2661bad2e3038214L,
            0x609c35d060cf37c1L,
            0x4ba03e78929b6807L,
            -0xb79aadd577c7deaL,
            -0x10723f52c0a5fa7fL,
            -0x7aec4fa5cd62fb14L,
            0x2295dbef5a603ebcL,
            -0x2fda638a0578ee4eL,
            0x311f78657cb7ecb9L,
            0x771d03baa6accef1L,
            0x596d9c3bde77e873L,
            -0x23e889efbafbad25L,
            0x5b85d931e890ef5eL,
            0x261f88eedccbbd36L,
            -0x3458e3eefed8eec7L,
            0xa3a125d270c03cL,
            -0x3be162951b1062aaL,
            -0x64de2b299fae840L,
            0x409d87f99aeb3ea9L,
            0x92d8e70ae59a864L,
            -0xb1ed2d688bb2fa6L,
            -0x276b355fc2b9e242L,
            -0x662900ce877f0cfbL,
            0x145d42da3710d23aL,
            0x2812adb381c1d64aL,
            -0x26fdabacd4ba1cddL,
            -0x5344bc4897584d8aL,
            0x74573f58c60c3ddfL,
            -0x38d464bd58344297L,
            -0x2eed67c8415e6f59L,
            -0x584dfbe731a2b907L,
            -0x592f6b2d5e9909a7L,
            0x10f66ed93811576eL,
            0x28d3553af07b8cfaL,
            -0x22c4a8232b26713eL,
            -0x32a84b053350689cL,
            0x1e4001ee8b46813aL,
            -0x3860a8b66c76fd62L,
            -0xb07bebd24d2898dL,
            -0x503676f12506f7aL,
            -0x3abb8dad73f03268L,
            -0x2c00b100be93fd49L,
            0x47c8414e9fa28367L,
            0x78f0171da51288e6L,
            0x7f5046c28cd1b43aL,
            -0x3c725310e6e52e10L,
            0x6210c0aba8230563L,
            0x15e3cd836648fe66L,
            0x56a1797408568c1eL,
            -0x6e9d164862b09977L,
            0x6fc7ba8e6135592dL,
            0x569e7feab218d54aL,
            -0x6c2de553cf09fd61L,
            0x4e7a938ca19a5fe5L,
            0x3c7dd68323efe355L,
            0x651993620ca49e3fL,
            0x9f0cc9127f8eca7L,
            0x3963f278753c4f44L,
            0x3f2ab0d0e62bb19fL,
            0x4d72a64283465629L,
            -0x2626a7d7da9b5679L,
            -0x21fe6b6d1be9b26cL,
            -0x3ce604d82e2bdbabL,
            -0x18770d74a75f3fdbL,
            -0x544c0d35a8ecc74cL,
            -0x553b5bf0dd824d98L,
            -0x70795a9fabb628a6L,
            -0x33c66642c378dea0L,
            0x3010e16e331a57e2L,
            0xd43cfd0741d4ed2L,
            0x7954298caa472790L,
            0xfe5b6444abb41ceL,
            -0x500c4ef2ddd50151L,
            -0x5abc7452db5a9d62L,
            -0x3b8b05a1d00cccd7L,
            -0x425f7c43a284c7d5L,
            -0x10ce5e325fe998c2L,
            -0x2564674a7448100bL,
            0xe001283d41a1576L,
            0x6ee0f9ab35eb17ebL,
            0x5de93fcf7e7e0169L,
            0x3cd1756a735b7caL,
            0x582ded067b6714e9L,
            0x56194735c4168e94L,
            -0x1150a5c623089f78L,
            -0x62656382eadf990L,
            -0x467282bfa5e8886bL,
            0x3281c2365b5bc415L,
            -0x7a1b304dc67f074cL,
            0x484aee59fa5880bdL,
            -0x1fff0d255df87fe8L,
            -0x1400c5b4008da2ddL,
            -0x7fc1c3c22d8e98fdL,
            0x413e18195eb5b4bfL,
            -0x31e15be86b013aafL,
            -0x3409a1ca91d29643L,
            0x654a616738582ba7L,
            0x62e46d535f11c417L,
            -0x42eee7afcbde705eL,
            0x7c715d440eaa5fb1L,
            0xe68ad0d758ade8dL,
            0x3242a4d88ac3ba92L,
            0x10f1e6939ee06b78L,
            -0x69a263bef654914cL,
            0x6bc256008b6083d5L,
            -0x5704c469991f14b3L,
            0x2d8a83366565a273L,
            -0x5a12221d633a603cL,
            -0x2e08236799314e7L,
            -0x793ec167d8d58147L,
            0x11149397f635b42cL,
            -0x3407dda71d47a40bL,
            0x37215737b1ab86fbL,
            0x44e5126c5c5f4ae5L,
            -0x66018331a79b646dL,
            -0x3baa192238417f10L,
            -0x6c413699bb178ddL,
            0x130dc4e99fb989e8L,
            -0x4fe8cb05023acf73L,
            -0x7021aba42b734d02L,
            0x1102c89b77b4b405L,
            0x2cd24ed5816eca6eL,
            -0x142a9b8c5afd49c1L,
            0x357fb8e6b489be97L,
            -0x1e9c56b6a1929826L,
            -0x78bee53cb428c666L,
            -0x743e727b0bdc841L,
            0x43702207d2269e74L,
            0x37a3eec07a419e21L,
            0x7fe4605c33d4ac0cL,
            0x6df566b6925a898dL,
            -0x76ad93d9626dda50L,
            -0x3db553c48ce2cc2L,
            0x2518f6ea6300c3caL,
            -0x1b1df024dfc2860bL
        )
    }

    @Test
    fun `MurmurHash3 returns back expected hashes using strings`() {
        testString("", 0, 0) //empty string with zero seed should give zero
        testString("", 1, 0x514E28B7)
        testString("", 0xffffffffL.toInt(), 0x81F16F39L.toInt()) //make sure seed value is handled unsigned
        testString("\u0000".repeat(4), 0, 0x2362F9DE) //make sure we handle embedded nulls

        testString("aaaa", 0x9747b28cL.toInt(), 0x5A97808A) //one full chunk
        testString("aaa", 0x9747b28cL.toInt(), 0x283E0130) //three characters
        testString("aa", 0x9747b28cL.toInt(), 0x5D211726) //two characters
        testString("a", 0x9747b28cL.toInt(), 0x7FA09EA6) //one character

        //Endian order within the chunks
        testString("abcd", 0x9747b28cL.toInt(), 0xF0478627L.toInt()) //one full chunk
        testString("abc", 0x9747b28cL.toInt(), 0xC84A62DDL.toInt())
        testString("ab", 0x9747b28cL.toInt(), 0x74875592)
        testString("a", 0x9747b28cL.toInt(), 0x7FA09EA6)

        testString("Hello, world!", 0x9747b28cL.toInt(), 0x24884CBA)

        testString("ππππππππ", 0x9747b28cL.toInt(), 0xD58063C1L.toInt())

        //String of 256 characters
        testString("a".repeat(256), 0x9747b28cL.toInt(), 0x37405BDC)
    }

    private fun testString(str: String, seed: Int, expected: Int) {
        val hash = MurmurHash3.hash32(str, 0, str.length, seed)
        assertEquals(expected, hash)
    }

    @Test
    fun `correct values are returned when different offsets are used`() {
        val bytes = "Now is the time for all good men to come to the aid of their country".toByteArray(charset("UTF-8"))
        var hash = 0
        for (i in bytes.indices) {
            hash = hash * 31 + (bytes[i] and 0xff.toByte())
            bytes[i] = hash.toByte()
        }

        // test different offsets.
        for (offset in 0..19) {
            // put the original bytes at the offset so the same hash will be generated
            val arr = ByteArray(bytes.size + offset)
            System.arraycopy(bytes, 0, arr, offset, bytes.size)
            var seed = 1
            val result = LongPair()
            for (len in bytes.indices) {
                seed *= -0x61c8864f
                val h: Int = MurmurHash3.hash32(arr, offset, len, seed)
                assertEquals(answers32[len], h)
                MurmurHash3.hash128(arr, offset, len, seed, result)
                assertEquals(answers128[len * 2], result.val1)
                assertEquals(answers128[len * 2 + 1], result.val2)
            }
        }
    }
}