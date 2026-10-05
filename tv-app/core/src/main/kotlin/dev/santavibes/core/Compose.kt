package dev.santavibes.core

// Shader composition — port of authoring/src/compose.ts (the reference implementation of
// docs/FORMATO-CENA.md, "Composição do shader"). MUST produce byte-identical output;
// authoring/test/fixtures/compose-cases.json is the shared contract (see ComposeTest).

class LineOrigin(val file: String, val line: Int)

class Composed(val source: String, val origins: List<LineOrigin>)

class MappedError(val file: String, val line: Int, val message: String)

object Compose {
    val INCLUDE_RE = Regex("^\\s*#include\\s+\"([^\"]+)\"\\s*$")

    fun paramUniformDecl(p: ParamDef): String =
        "uniform ${if (p.type == ParamType.COLOR) "vec3" else "float"} p_${p.id};"

    /** Scan a body (and, recursively, its includes) and load every referenced file once. */
    fun collectIncludes(
        body: String,
        load: (String) -> String?,
        into: MutableMap<String, String?> = LinkedHashMap(),
    ): MutableMap<String, String?> {
        for (line in body.split("\n")) {
            val path = INCLUDE_RE.find(line)?.groupValues?.get(1) ?: continue
            if (into.containsKey(path)) continue
            val text = try { load(path) } catch (_: Exception) { null }
            into[path] = text
            if (text != null) collectIncludes(text, load, into)
        }
        return into
    }

    private fun splitLines(text: String): List<String> {
        val lines = text.replace(Regex("\r\n?"), "\n").split("\n").toMutableList()
        if (lines.size > 1 && lines.last() == "") lines.removeAt(lines.size - 1)
        return lines
    }

    fun compose(header: String, params: List<ParamDef>, body: String, bodyFile: String, includes: Map<String, String?>): Composed {
        val out = ArrayList<String>()
        val origins = ArrayList<LineOrigin>()
        val included = HashSet<String>()

        fun push(line: String, file: String, lineNo: Int) {
            out.add(line)
            origins.add(LineOrigin(file, lineNo))
        }

        fun emitBody(text: String, file: String) {
            val lines = splitLines(text)
            for ((i, line) in lines.withIndex()) {
                val inc = INCLUDE_RE.find(line)?.groupValues?.get(1)
                if (inc == null) {
                    push(line, file, i + 1)
                    continue
                }
                if (inc in included) {
                    push("// #include \"$inc\" (already included)", file, i + 1)
                    continue
                }
                included.add(inc)
                val text2 = includes[inc]
                if (text2 == null) {
                    push("#error include not found: \"$inc\"", file, i + 1)
                    continue
                }
                emitBody(text2, "shared/glsl/$inc")
            }
        }

        splitLines(header).forEachIndexed { i, l -> push(l, "shared/glsl/header.glsl", i + 1) }
        params.forEachIndexed { i, p -> push(paramUniformDecl(p), "<params>", i + 1) }
        emitBody(body, bodyFile)
        return Composed(out.joinToString("\n") + "\n", origins)
    }

    // Browsers/ANGLE: "ERROR: 0:12: message"; Mali/Adreno similar; PowerVR may omit the
    // severity ("0:12: L0001: message"). Unknown lines are kept with file "".
    private val LOG_RE = Regex("^(?:(ERROR|WARNING):\\s*)?\\d+:(\\d+):\\s*(.*)$")

    fun mapErrorLog(log: String, composed: Composed): List<MappedError> {
        val out = ArrayList<MappedError>()
        for (raw in log.split("\n")) {
            val text = raw.trim()
            if (text.isEmpty()) continue
            val m = LOG_RE.find(text)
            if (m == null) {
                out.add(MappedError("", 0, text))
                continue
            }
            val composedLine = m.groupValues[2].toInt()
            val origin = composed.origins.getOrNull(composedLine - 1)
            val prefix = if (m.groupValues[1] == "WARNING") "warning: " else ""
            out.add(MappedError(origin?.file ?: "", origin?.line ?: composedLine, prefix + m.groupValues[3]))
        }
        return out
    }
}
