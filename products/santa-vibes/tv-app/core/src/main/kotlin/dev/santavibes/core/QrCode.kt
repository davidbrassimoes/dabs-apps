package dev.santavibes.core

/**
 * Minimal QR code encoder (ISO/IEC 18004) for the control page address on the TV: byte mode,
 * error correction level M, versions 1–6 (up to 106 bytes, enough for a URL), mask chosen by the
 * standard penalty rules unless one is forced. No dependency (ADR 0002). Not for the render loop.
 *
 * Checked against matrices from the `qrcode` npm package (test/resources/qr-cases.json).
 */
class QrCode private constructor(val version: Int, val mask: Int, private val modules: Array<BooleanArray>) {
    val size: Int get() = modules.size

    /** True = dark module. Row `y`, column `x`, origin top-left. */
    fun get(x: Int, y: Int): Boolean = modules[y][x]

    companion object {
        private const val MAX_VERSION = 6
        // Level M, versions 1..6.
        private val ECC_PER_BLOCK = intArrayOf(10, 16, 26, 18, 24, 16)
        private val BLOCKS = intArrayOf(1, 1, 1, 2, 2, 4)
        private const val FORMAT_ECL_BITS = 0 // M

        /** Smallest version that fits `data`; `forceMask` 0..7 skips the penalty search. */
        fun encode(data: ByteArray, forceMask: Int = -1): QrCode {
            val version = (1..MAX_VERSION).firstOrNull { dataCapacityBits(it) >= 4 + 8 + data.size * 8 }
                ?: throw IllegalArgumentException("too long for a version $MAX_VERSION-M QR code: ${data.size} bytes")
            val codewords = addEcc(dataCodewords(data, version), version)
            var best: QrCode? = null
            var bestPenalty = Int.MAX_VALUE
            for (m in if (forceMask in 0..7) forceMask..forceMask else 0..7) {
                val qr = build(version, m, codewords)
                val p = if (forceMask in 0..7) 0 else penalty(qr.modules)
                if (p < bestPenalty) {
                    bestPenalty = p
                    best = qr
                }
            }
            return best!!
        }

        fun encode(text: String, forceMask: Int = -1) = encode(text.toByteArray(Charsets.UTF_8), forceMask)

        // ------------------------------------------------------------ codewords

        private fun rawDataModules(ver: Int): Int {
            var r = (16 * ver + 128) * ver + 64
            if (ver >= 2) {
                val align = ver / 7 + 2
                r -= (25 * align - 10) * align - 55
            }
            return r
        }

        private fun dataCapacityBits(ver: Int) = (rawDataModules(ver) / 8 - ECC_PER_BLOCK[ver - 1] * BLOCKS[ver - 1]) * 8

        private fun dataCodewords(data: ByteArray, ver: Int): IntArray {
            val capBits = dataCapacityBits(ver)
            val bits = ArrayList<Boolean>(capBits)
            fun put(v: Int, n: Int) { for (i in n - 1 downTo 0) bits.add((v ushr i) and 1 == 1) }
            put(0b0100, 4) // byte mode
            put(data.size, 8) // count indicator: 8 bits for versions 1..9
            for (b in data) put(b.toInt() and 0xFF, 8)
            put(0, minOf(4, capBits - bits.size)) // terminator
            put(0, (8 - bits.size % 8) % 8)
            var pad = 0xEC
            while (bits.size < capBits) {
                put(pad, 8)
                pad = pad xor (0xEC xor 0x11)
            }
            return IntArray(capBits / 8) { i -> (0 until 8).fold(0) { acc, j -> (acc shl 1) or (if (bits[i * 8 + j]) 1 else 0) } }
        }

        private fun addEcc(data: IntArray, ver: Int): IntArray {
            val blocks = BLOCKS[ver - 1]
            val ecc = ECC_PER_BLOCK[ver - 1]
            val raw = rawDataModules(ver) / 8
            val shortBlocks = blocks - raw % blocks
            val shortLen = raw / blocks
            val divisor = rsDivisor(ecc)
            val dataBlocks = ArrayList<IntArray>()
            val eccBlocks = ArrayList<IntArray>()
            var k = 0
            for (i in 0 until blocks) {
                val len = shortLen - ecc + (if (i < shortBlocks) 0 else 1)
                val d = data.copyOfRange(k, k + len)
                k += len
                dataBlocks.add(d)
                eccBlocks.add(rsRemainder(d, divisor))
            }
            val out = ArrayList<Int>(raw)
            for (i in 0 until shortLen - ecc + 1) for (b in dataBlocks) if (i < b.size) out.add(b[i])
            for (i in 0 until ecc) for (b in eccBlocks) out.add(b[i])
            return out.toIntArray()
        }

        private fun gfMul(a: Int, b: Int): Int {
            var z = 0
            for (i in 7 downTo 0) {
                z = (z shl 1) xor ((z ushr 7) * 0x11D)
                z = z xor (((b ushr i) and 1) * a)
            }
            return z
        }

        private fun rsDivisor(degree: Int): IntArray {
            val r = IntArray(degree)
            r[degree - 1] = 1
            var root = 1
            for (i in 0 until degree) {
                for (j in 0 until degree) {
                    r[j] = gfMul(r[j], root)
                    if (j + 1 < degree) r[j] = r[j] xor r[j + 1]
                }
                root = gfMul(root, 2)
            }
            return r
        }

        private fun rsRemainder(data: IntArray, divisor: IntArray): IntArray {
            val r = IntArray(divisor.size)
            for (b in data) {
                val factor = b xor r[0]
                System.arraycopy(r, 1, r, 0, r.size - 1)
                r[r.size - 1] = 0
                for (i in r.indices) r[i] = r[i] xor gfMul(divisor[i], factor)
            }
            return r
        }

        // ------------------------------------------------------------ matrix

        private fun build(ver: Int, mask: Int, codewords: IntArray): QrCode {
            val size = ver * 4 + 17
            val m = Array(size) { BooleanArray(size) }
            val fn = Array(size) { BooleanArray(size) } // function modules: not data, not masked
            fun set(x: Int, y: Int, dark: Boolean) { m[y][x] = dark; fn[y][x] = true }

            for (i in 0 until size) { set(6, i, i % 2 == 0); set(i, 6, i % 2 == 0) } // timing
            fun finder(cx: Int, cy: Int) {
                for (dy in -4..4) for (dx in -4..4) {
                    val x = cx + dx
                    val y = cy + dy
                    if (x !in 0 until size || y !in 0 until size) continue
                    val d = maxOf(Math.abs(dx), Math.abs(dy))
                    set(x, y, d != 2 && d != 4)
                }
            }
            finder(3, 3); finder(size - 4, 3); finder(3, size - 4)
            if (ver >= 2) { // one alignment pattern for versions 2..6
                val p = size - 7
                for (dy in -2..2) for (dx in -2..2) set(p + dx, p + dy, maxOf(Math.abs(dx), Math.abs(dy)) != 1)
            }
            drawFormat(m, fn, mask, reserveOnly = true)

            // zigzag data placement
            var i = 0
            var right = size - 1
            while (right >= 1) {
                if (right == 6) right = 5
                for (vert in 0 until size) {
                    for (j in 0..1) {
                        val x = right - j
                        val upward = ((right + 1) and 2) == 0
                        val y = if (upward) size - 1 - vert else vert
                        if (!fn[y][x] && i < codewords.size * 8) {
                            m[y][x] = (codewords[i ushr 3] ushr (7 - (i and 7))) and 1 == 1
                            i++
                        }
                    }
                }
                right -= 2
            }
            for (y in 0 until size) for (x in 0 until size) if (!fn[y][x] && maskBit(mask, x, y)) m[y][x] = !m[y][x]
            drawFormat(m, fn, mask, reserveOnly = false)
            return QrCode(ver, mask, m)
        }

        private fun maskBit(mask: Int, x: Int, y: Int): Boolean = when (mask) {
            0 -> (x + y) % 2 == 0
            1 -> y % 2 == 0
            2 -> x % 3 == 0
            3 -> (x + y) % 3 == 0
            4 -> (x / 3 + y / 2) % 2 == 0
            5 -> x * y % 2 + x * y % 3 == 0
            6 -> (x * y % 2 + x * y % 3) % 2 == 0
            else -> ((x + y) % 2 + x * y % 3) % 2 == 0
        }

        private fun drawFormat(m: Array<BooleanArray>, fn: Array<BooleanArray>, mask: Int, reserveOnly: Boolean) {
            val size = m.size
            val data = FORMAT_ECL_BITS shl 3 or mask
            var rem = data
            for (i in 0 until 10) rem = (rem shl 1) xor ((rem ushr 9) * 0x537)
            val bits = (data shl 10 or rem) xor 0x5412
            fun bit(i: Int) = !reserveOnly && (bits ushr i) and 1 == 1
            fun set(x: Int, y: Int, v: Boolean) { m[y][x] = v; fn[y][x] = true }
            for (i in 0..5) set(8, i, bit(i))
            set(8, 7, bit(6)); set(8, 8, bit(7)); set(7, 8, bit(8))
            for (i in 9 until 15) set(14 - i, 8, bit(i))
            for (i in 0 until 8) set(size - 1 - i, 8, bit(i))
            for (i in 8 until 15) set(8, size - 15 + i, bit(i))
            set(8, size - 8, true) // the dark module
        }

        // ------------------------------------------------------------ mask penalty (ISO 18004 §7.8.3, read as the qrcode npm package does)

        private fun penalty(m: Array<BooleanArray>): Int {
            val n = m.size
            var p = 0
            for (pass in 0..1) { // rows, then columns
                for (a in 0 until n) {
                    fun at(b: Int) = if (pass == 0) m[a][b] else m[b][a]
                    var run = 1
                    for (b in 1..n) {
                        if (b < n && at(b) == at(b - 1)) run++ else {
                            if (run >= 5) p += run - 2
                            run = 1
                        }
                    }
                    var window = 0 // last 11 modules, newest in bit 0; only full windows count
                    for (b in 0 until n) {
                        window = ((window shl 1) and 0x7FF) or (if (at(b)) 1 else 0)
                        if (b >= 10 && (window == 0x5D0 || window == 0x05D)) p += 40 // 1:1:3:1:1 plus 4 light
                    }
                }
            }
            for (y in 0 until n - 1) for (x in 0 until n - 1) {
                val c = m[y][x]
                if (c == m[y][x + 1] && c == m[y + 1][x] && c == m[y + 1][x + 1]) p += 3
            }
            val dark = m.sumOf { r -> r.count { it } }
            val total = n * n
            p += Math.abs(Math.ceil(dark * 100.0 / total / 5).toInt() - 10) * 10
            return p
        }
    }
}
