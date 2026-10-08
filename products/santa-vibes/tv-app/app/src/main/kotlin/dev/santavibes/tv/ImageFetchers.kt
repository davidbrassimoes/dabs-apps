package dev.santavibes.tv

import dev.santavibes.core.ImageSources

/**
 * Candidates from each image source (docs/FONTES-IMAGEM.md). The parsing is in core
 * ([ImageSources]); this only does the HTTP. Runs on the provider's background thread.
 */
class ImageFetchers(private val serverBase: String) : ImageProvider.CandidateFetcher {
    override fun candidates(source: String): List<ImageSources.Candidate> = when (source) {
        ImageSources.SERVER -> ImageSources.parseListing(text(serverBase), serverBase)
        else -> emptyList()
    }

    private fun text(url: String) = String(ImageProvider.httpGet(url, MAX_TEXT), Charsets.UTF_8)

    private companion object {
        const val MAX_TEXT = 4 * 1024 * 1024
    }
}
