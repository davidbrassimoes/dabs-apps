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
    const val MET = "met"
    const val NASA = "nasa"
    // The Art Institute of Chicago (the first proposal) is not here: since at least 2026-10-08 its
    // IIIF images answer non-browser clients with a Cloudflare challenge (FONTES-IMAGEM.md).
    val ALL = listOf(SERVER, MET, NASA)

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

/**
 * The Metropolitan Museum of Art open access API (checked 2026-10-08): `v1.1/search` gives object
 * ids (paginated; its public-domain filter is not reliable), each object gives `isPublicDomain` and
 * a `primaryImageSmall` ("web-large", ~1000 px). No key.
 */
object MetSource {
    private const val BASE = "https://collectionapi.metmuseum.org/public/collection"
    /** Themes that suit a room's light; one per refresh, in turn. */
    val QUERIES = listOf("landscape", "seascape", "sky", "flowers", "garden", "river", "sunset", "forest")

    fun searchUrl(query: String, offset: Int, limit: Int) =
        "$BASE/v1.1/search?q=${enc(query)}&hasImages=true&isPublicDomain=true&medium=Paintings&limit=$limit&offset=$offset"

    fun objectUrl(id: Long) = "$BASE/v1/objects/$id"

    @Suppress("UNCHECKED_CAST")
    fun parseSearch(json: String): List<Long> {
        val o = Json.parse(json) as? Map<String, Any?> ?: return emptyList()
        return (o["objectIDs"] as? List<Any?>)?.mapNotNull { (it as? Double)?.toLong() } ?: emptyList()
    }

    /** Null unless public domain with an image. */
    @Suppress("UNCHECKED_CAST")
    fun parseObject(json: String): ImageSources.Candidate? {
        val o = Json.parse(json) as? Map<String, Any?> ?: return null
        if (o["isPublicDomain"] != true) return null
        val url = o["primaryImageSmall"] as? String
        if (url.isNullOrBlank() || !url.startsWith("https://")) return null
        val id = (o["objectID"] as? Double)?.toLong() ?: return null
        val date = (o["objectDate"] as? String).orEmpty()
        val title = (o["title"] as? String).orEmpty() + if (date.isNotBlank()) " ($date)" else ""
        return ImageSources.Candidate(ImageSources.MET, id.toString(), url, title, (o["artistDisplayName"] as? String).orEmpty(), "The Met", "Public Domain (CC0)")
    }
}

/**
 * NASA Image and Video Library (checked 2026-10-08): `images-api.nasa.gov/search`, no key; each
 * item lists its renditions, of which `~medium.jpg` (~1280 px) suits the TV. NASA imagery is
 * generally not copyrighted; the credit (`secondary_creator` or centre) is kept for the overlay.
 */
object NasaSource {
    val QUERIES = listOf("nebula", "galaxy", "aurora", "earth from space", "hubble", "jupiter", "saturn", "star cluster")

    fun searchUrl(query: String, page: Int, pageSize: Int) =
        "https://images-api.nasa.gov/search?q=${enc(query)}&media_type=image&page=$page&page_size=$pageSize"

    @Suppress("UNCHECKED_CAST")
    fun parseSearch(json: String): List<ImageSources.Candidate> {
        val root = Json.parse(json) as? Map<String, Any?> ?: return emptyList()
        val items = (root["collection"] as? Map<String, Any?>)?.get("items") as? List<Map<String, Any?>> ?: return emptyList()
        return items.mapNotNull { item ->
            val d = (item["data"] as? List<Map<String, Any?>>)?.firstOrNull() ?: return@mapNotNull null
            if (d["media_type"] != null && d["media_type"] != "image") return@mapNotNull null
            val id = d["nasa_id"] as? String ?: return@mapNotNull null
            val links = (item["links"] as? List<Map<String, Any?>>).orEmpty().mapNotNull { it["href"] as? String }
            val url = links.firstOrNull { it.endsWith("~medium.jpg") } ?: links.firstOrNull { it.endsWith("~small.jpg") } ?: return@mapNotNull null
            if (!url.startsWith("https://")) return@mapNotNull null
            val credit = (d["secondary_creator"] as? String)?.takeIf { it.isNotBlank() } ?: (d["center"] as? String).orEmpty()
            ImageSources.Candidate(ImageSources.NASA, id, url, (d["title"] as? String).orEmpty(), credit, "NASA", "NASA (sem direitos de autor, salvo indicação)")
        }
    }
}

private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
