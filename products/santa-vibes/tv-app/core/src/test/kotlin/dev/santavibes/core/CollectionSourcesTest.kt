package dev.santavibes.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parsing of the collection APIs, on responses shaped like the real ones (checked 2026-10-08). */
class CollectionSourcesTest {
    @Test
    fun metSearchAndObjects() {
        assertEquals(listOf(340468L, 10181L), MetSource.parseSearch("""{"total":34602,"objectIDs":[340468,10181]}"""))
        assertTrue(MetSource.parseSearch("""{"total":0,"objectIDs":null}""").isEmpty())
        val ok = MetSource.parseObject(
            """{"objectID":10181,"isPublicDomain":true,"primaryImageSmall":"https://images.metmuseum.org/CRDImages/ad/web-large/ap29.35.jpg",
               "title":"Landscape","artistDisplayName":"Ralph Albert Blakelock","objectDate":"1869"}""",
        )!!
        assertEquals("met:10181", ok.key)
        assertEquals("Landscape (1869)", ok.title)
        assertEquals("Ralph Albert Blakelock", ok.artist)
        assertEquals("https://images.metmuseum.org/CRDImages/ad/web-large/ap29.35.jpg", ok.url)
        assertNull(MetSource.parseObject("""{"objectID":892740,"isPublicDomain":false,"primaryImageSmall":""}"""))
        assertNull(MetSource.parseObject("""{"objectID":1,"isPublicDomain":true,"primaryImageSmall":""}"""))
        assertTrue(MetSource.searchUrl("sea sky", 40, 20).contains("v1.1/search?q=sea+sky&hasImages=true&isPublicDomain=true"))
    }

    @Test
    fun nasaSearch() {
        val json = """{"collection":{"items":[
            {"href":"https://images-assets.nasa.gov/image/PIA14417/collection.json",
             "data":[{"title":"Weighing in on the Dumbbell Nebula","nasa_id":"PIA14417","center":"JPL","media_type":"image",
                      "secondary_creator":"NASA/JPL-Caltech"}],
             "links":[{"href":"https://images-assets.nasa.gov/image/PIA14417/PIA14417~medium.jpg","rel":"alternate"},
                      {"href":"https://images-assets.nasa.gov/image/PIA14417/PIA14417~thumb.jpg","rel":"preview"}]},
            {"data":[{"title":"no links","nasa_id":"X"}]},
            {"data":[{"title":"small only","nasa_id":"Y","center":"GSFC"}],
             "links":[{"href":"https://images-assets.nasa.gov/image/Y/Y~small.jpg"}]}
        ]}}"""
        val c = NasaSource.parseSearch(json)
        assertEquals(listOf("nasa:PIA14417", "nasa:Y"), c.map { it.key })
        assertEquals("https://images-assets.nasa.gov/image/PIA14417/PIA14417~medium.jpg", c[0].url)
        assertEquals("NASA/JPL-Caltech", c[0].artist)
        assertEquals("GSFC", c[1].artist)
        assertTrue(NasaSource.searchUrl("earth from space", 2, 20).endsWith("q=earth+from+space&media_type=image&page=2&page_size=20"))
    }
}
