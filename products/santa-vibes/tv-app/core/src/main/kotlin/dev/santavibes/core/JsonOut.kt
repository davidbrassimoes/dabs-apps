package dev.santavibes.core

/**
 * The writing half of [Json], for the control API and the files kept on the box (3.2–3.4):
 * Map (string keys), List, String, Number, Boolean and null. Whole numbers print without
 * ".0"; NaN and infinities print as null. Not for the render loop.
 */
object JsonOut {
    fun write(v: Any?): String = StringBuilder().also { put(it, v) }.toString()

    private fun put(sb: StringBuilder, v: Any?) {
        when (v) {
            null -> sb.append("null")
            is String -> string(sb, v)
            is Boolean -> sb.append(v)
            is Int, is Long -> sb.append(v)
            is Number -> number(sb, v.toDouble())
            is Map<*, *> -> {
                sb.append('{')
                var first = true
                for ((k, x) in v) {
                    if (!first) sb.append(',')
                    first = false
                    string(sb, k.toString())
                    sb.append(':')
                    put(sb, x)
                }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('[')
                var first = true
                for (x in v) {
                    if (!first) sb.append(',')
                    first = false
                    put(sb, x)
                }
                sb.append(']')
            }
            else -> string(sb, v.toString())
        }
    }

    private fun number(sb: StringBuilder, d: Double) {
        when {
            d.isNaN() || d.isInfinite() -> sb.append("null")
            d == Math.rint(d) && Math.abs(d) < 1e15 -> sb.append(d.toLong())
            else -> sb.append(d)
        }
    }

    private fun string(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c < ' ' || c == ' ' || c == ' ' -> sb.append("\\u%04x".format(c.code))
                else -> sb.append(c)
            }
        }
        sb.append('"')
    }
}
