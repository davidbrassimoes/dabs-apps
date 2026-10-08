package dev.santavibes.core

/**
 * Image sources (docs/FONTES-IMAGEM.md): what each one is called, and how to turn its responses
 * into candidates. Network and decoding live in the app; this part is pure and tested on the JVM.
 */
object ImageSources {
    /** A downloadable image with its credit. `id` is unique within the source. */
    class Candidate(
        val source: String,
        val id: String,
        val url: String,
        val title: String = "",
        val artist: String = "",
        val institution: String = "",
        val license: String = "",
    ) {
        val key: String get() = "$source:$id"
    }

    const val SERVER = "server"
    const val AIC = "aic"
    const val MET = "met"
    const val NASA = "nasa"
    val ALL = listOf(SERVER, AIC, MET, NASA)

    /** `--es images`: "all", "off", or a comma list of source names. Returns null for off. */
    fun parseSelection(s: String?): Set<String>? = when (s?.trim()) {
        null, "", "all" -> ALL.toSet()
        "off", "none" -> null
        else -> s.split(',').map { it.trim() }.filter { it in ALL }.toSet().ifEmpty { null }
    }

    private val HREF = Regex("""href\s*=\s*"([^"?#]+\.(?:jpe?g|png|webp))"""", RegexOption.IGNORE_CASE)

    /**
     * A directory listing (Apache mod_autoindex or `python -m http.server`): every linked
     * .jpg/.jpeg/.png/.webp, as absolute URLs under `base` (which ends with '/').
     */
    fun parseListing(html: String, base: String): List<Candidate> {
        val seen = LinkedHashSet<String>()
        for (m in HREF.findAll(html)) {
            val href = m.groupValues[1]
            if (href.startsWith("/") || href.contains("://") || href.startsWith("..")) continue
            seen.add(href)
        }
        return seen.map { href ->
            val name = decode(href)
            Candidate(SERVER, name, base + href, title = name.substringBeforeLast('.'), institution = "pasta de casa")
        }
    }

    /** %XX decoding for file names (UTF-8). */
    fun decode(s: String): String {
        if ('%' !in s) return s
        val out = java.io.ByteArrayOutputStream()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '%' && i + 2 < s.length) {
                out.write(s.substring(i + 1, i + 3).toInt(16))
                i += 3
            } else {
                out.write(c.toString().toByteArray(Charsets.UTF_8))
                i++
            }
        }
        return out.toString("UTF-8")
    }
}
