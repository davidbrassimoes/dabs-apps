package dev.santavibes.core

/**
 * Minimal JSON reader for scene.json, profiles and test fixtures (RFC 8259, no extensions).
 * Objects become [Map] (insertion order kept), arrays [List], numbers [Double],
 * plus [String], [Boolean] and null. Load-time only — never used per frame.
 * Kept in-house so the tv-app has no runtime dependency (docs/decisoes/0002).
 */
object Json {
    class ParseException(message: String, val offset: Int) : RuntimeException("$message at offset $offset")

    fun parse(text: String): Any? {
        val p = Parser(text)
        p.skipWs()
        val v = p.value()
        p.skipWs()
        if (p.i != text.length) throw ParseException("trailing characters", p.i)
        return v
    }

    private class Parser(val s: String) {
        var i = 0

        fun skipWs() {
            while (i < s.length && (s[i] == ' ' || s[i] == '\t' || s[i] == '\n' || s[i] == '\r')) i++
        }

        fun value(): Any? {
            if (i >= s.length) throw ParseException("unexpected end", i)
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> if (c == '-' || c in '0'..'9') num() else throw ParseException("unexpected '$c'", i)
            }
        }

        private fun lit(word: String, v: Any?): Any? {
            if (!s.startsWith(word, i)) throw ParseException("expected $word", i)
            i += word.length
            return v
        }

        private fun obj(): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>()
            i++
            skipWs()
            if (peek() == '}') { i++; return m }
            while (true) {
                skipWs()
                if (peek() != '"') throw ParseException("expected key", i)
                val k = str()
                skipWs()
                expect(':')
                skipWs()
                m[k] = value()
                skipWs()
                when (peek()) {
                    ',' -> i++
                    '}' -> { i++; return m }
                    else -> throw ParseException("expected , or }", i)
                }
            }
        }

        private fun arr(): List<Any?> {
            val l = ArrayList<Any?>()
            i++
            skipWs()
            if (peek() == ']') { i++; return l }
            while (true) {
                skipWs()
                l.add(value())
                skipWs()
                when (peek()) {
                    ',' -> i++
                    ']' -> { i++; return l }
                    else -> throw ParseException("expected , or ]", i)
                }
            }
        }

        private fun str(): String {
            expect('"')
            val b = StringBuilder()
            while (true) {
                if (i >= s.length) throw ParseException("unterminated string", i)
                val c = s[i++]
                when {
                    c == '"' -> return b.toString()
                    c == '\\' -> {
                        if (i >= s.length) throw ParseException("bad escape", i)
                        when (val e = s[i++]) {
                            '"' -> b.append('"')
                            '\\' -> b.append('\\')
                            '/' -> b.append('/')
                            'b' -> b.append('\b')
                            'f' -> b.append('\u000C')
                            'n' -> b.append('\n')
                            'r' -> b.append('\r')
                            't' -> b.append('\t')
                            'u' -> {
                                if (i + 4 > s.length) throw ParseException("bad \\u escape", i)
                                b.append(s.substring(i, i + 4).toInt(16).toChar())
                                i += 4
                            }
                            else -> throw ParseException("bad escape '\\$e'", i - 1)
                        }
                    }
                    c < ' ' -> throw ParseException("control character in string", i - 1)
                    else -> b.append(c)
                }
            }
        }

        private fun num(): Double {
            val start = i
            if (peek() == '-') i++
            while (i < s.length && (s[i] in '0'..'9' || s[i] == '.' || s[i] == 'e' || s[i] == 'E' || s[i] == '+' || s[i] == '-')) i++
            return s.substring(start, i).toDoubleOrNull() ?: throw ParseException("bad number", start)
        }

        private fun peek(): Char = if (i < s.length) s[i] else '\u0000'

        private fun expect(c: Char) {
            if (peek() != c) throw ParseException("expected '$c'", i)
            i++
        }
    }
}
