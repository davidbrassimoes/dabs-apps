package dev.santavibes.tv

import dev.santavibes.core.ImageSources
import dev.santavibes.core.MetSource
import dev.santavibes.core.NasaSource
import kotlin.random.Random

/**
 * Candidates from each image source (docs/FONTES-IMAGEM.md). The parsing is in core
 * ([ImageSources], [MetSource], [NasaSource]); this does the HTTP, one theme per refresh in turn
 * and a random page, so the cache fills with variety. Runs on the provider's background thread.
 */
class ImageFetchers(private val serverBase: String) : ImageProvider.CandidateFetcher {
    private val random = Random(System.currentTimeMillis())
    private var metTurn = random.nextInt(MetSource.QUERIES.size)
    private var nasaTurn = random.nextInt(NasaSource.QUERIES.size)

    override fun candidates(source: String): List<ImageSources.Candidate> = when (source) {
        ImageSources.SERVER -> ImageSources.parseListing(text(serverBase), serverBase)
        ImageSources.MET -> met()
        ImageSources.NASA -> nasa()
        else -> emptyList()
    }

    private fun met(): List<ImageSources.Candidate> {
        val q = MetSource.QUERIES[metTurn++ % MetSource.QUERIES.size]
        val ids = MetSource.parseSearch(text(MetSource.searchUrl(q, random.nextInt(25) * PAGE, PAGE)))
        val out = ArrayList<ImageSources.Candidate>()
        for (id in ids) {
            runCatching { MetSource.parseObject(text(MetSource.objectUrl(id))) }.getOrNull()?.let(out::add)
            Thread.sleep(150) // well under the API's limit
        }
        return out
    }

    private fun nasa(): List<ImageSources.Candidate> {
        val q = NasaSource.QUERIES[nasaTurn++ % NasaSource.QUERIES.size]
        return NasaSource.parseSearch(text(NasaSource.searchUrl(q, 1 + random.nextInt(5), PAGE)))
    }

    private fun text(url: String) = String(ImageProvider.httpGet(url, MAX_TEXT), Charsets.UTF_8)

    private companion object {
        const val MAX_TEXT = 4 * 1024 * 1024
        const val PAGE = 20
    }
}
