package dev.santavibes.core

/**
 * The box's image cache index (docs/FONTES-IMAGEM.md): one entry per downloaded image, with its
 * credit and its extracted palette. LRU by bytes: past [maxBytes] the images shown longest ago
 * go first. Persisted as JSON next to the files; read with [Json], written by hand.
 *
 * Not thread-safe: the provider's background thread owns it.
 */
class ImageIndex(var maxBytes: Long = 300L * 1024 * 1024) {
    class Entry(
        /** "<source>:<id>", unique. */
        val key: String,
        val source: String,
        /** File name inside the cache folder. */
        val file: String,
        val bytes: Long,
        val title: String,
        val artist: String,
        val institution: String,
        val license: String,
        /** 15 floats, sRGB 0..1, dark to light ([PaletteExtract]). */
        val palette: FloatArray,
        /** Last time shown (or added), ms; the LRU order. */
        var lastUsed: Long,
    ) {
        /** One line for the overlay. */
        fun credit(): String = listOf(title, artist, institution).filter { it.isNotBlank() }.joinToString(" · ")
    }

    val entries = mutableListOf<Entry>()
    val totalBytes: Long get() = entries.sumOf { it.bytes }

    fun contains(key: String) = entries.any { it.key == key }

    /** Adds `e` (replacing one with the same key); returns the entries evicted to stay under [maxBytes]. */
    fun add(e: Entry): List<Entry> {
        entries.removeAll { it.key == e.key }
        entries.add(e)
        return trim(keep = e)
    }

    fun trim(keep: Entry? = null): List<Entry> {
        val evicted = mutableListOf<Entry>()
        while (totalBytes > maxBytes && entries.size > 1) {
            val oldest = entries.filter { it !== keep }.minByOrNull { it.lastUsed } ?: break
            entries.remove(oldest)
            evicted.add(oldest)
        }
        return evicted
    }

    /** The image to show next among `sources` (null = any): the one shown longest ago, other than `current`. */
    fun next(sources: Set<String>?, current: String?, now: Long): Entry? {
        val pick = entries.filter { (sources == null || it.source in sources) && it.key != current }.minByOrNull { it.lastUsed }
            ?: entries.firstOrNull { it.key == current && (sources == null || it.source in sources) }
        pick?.lastUsed = now
        return pick
    }

    fun toJson(): String {
        val sb = StringBuilder("{\"version\":1,\"entries\":[\n")
        entries.forEachIndexed { i, e ->
            sb.append("{\"key\":").append(q(e.key)).append(",\"source\":").append(q(e.source)).append(",\"file\":").append(q(e.file))
                .append(",\"bytes\":").append(e.bytes).append(",\"title\":").append(q(e.title)).append(",\"artist\":").append(q(e.artist))
                .append(",\"institution\":").append(q(e.institution)).append(",\"license\":").append(q(e.license))
                .append(",\"palette\":").append(e.palette.joinToString(",", "[", "]")).append(",\"lastUsed\":").append(e.lastUsed).append('}')
            sb.append(if (i < entries.size - 1) ",\n" else "\n")
        }
        return sb.append("]}\n").toString()
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromJson(text: String, maxBytes: Long): ImageIndex {
            val idx = ImageIndex(maxBytes)
            val root = Json.parse(text) as? Map<String, Any?> ?: return idx
            for (o in root["entries"] as? List<Map<String, Any?>> ?: emptyList()) {
                val pal = (o["palette"] as? List<Double>)?.map { it.toFloat() }?.toFloatArray() ?: continue
                if (pal.size != 15) continue
                idx.entries.add(
                    Entry(
                        o["key"] as? String ?: continue, o["source"] as? String ?: "", o["file"] as? String ?: continue,
                        (o["bytes"] as? Double)?.toLong() ?: 0L, o["title"] as? String ?: "", o["artist"] as? String ?: "",
                        o["institution"] as? String ?: "", o["license"] as? String ?: "", pal, (o["lastUsed"] as? Double)?.toLong() ?: 0L,
                    ),
                )
            }
            return idx
        }

        /** JSON string literal. */
        fun q(s: String): String {
            val sb = StringBuilder("\"")
            for (ch in s) {
                when {
                    ch == '"' -> sb.append("\\\"")
                    ch == '\\' -> sb.append("\\\\")
                    ch < ' ' -> sb.append(String.format("\\u%04x", ch.code))
                    else -> sb.append(ch)
                }
            }
            return sb.append('"').toString()
        }
    }
}
