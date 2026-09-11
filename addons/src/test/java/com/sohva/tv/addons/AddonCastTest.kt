package com.sohva.tv.addons

import org.junit.Assert.*
import org.junit.Test

class AddonCastTest {
    private fun parse(fields: String) = AddonMediaParser.metadata("""{"meta":{"id":"fixture","type":"movie","name":"Fixture",$fields}}""").cast

    @Test fun aiometadataRichCreditsKeepPhotosCharactersAndProviderOrder() {
        val cast = parse(""""app_extras":{"cast":[{"name":"Actor One","character":"Pilot","photo":"https://example.invalid/one.jpg"},{"name":"Actor Two","character":"Captain","photo":null}]},"cast":["Actor Two","Actor One"]""")
        assertEquals(listOf("Actor One", "Actor Two"), cast.map { it.name })
        assertEquals("https://example.invalid/one.jpg", cast[0].photo)
        assertEquals("Pilot", cast[0].character)
        assertNull(cast[1].photo)
        assertFalse(cast[0].toString().contains("Actor"))
        assertFalse(cast[0].toString().contains("example"))
    }

    @Test fun standardNameArraysAndActorLinksRemainCompatible() {
        val cast = parse(""""cast":[" Actor One ","Actor Two","actor one",null,23,""],"links":[{"category":"actor","name":"Actor Three","url":"stremio:///search?search=Actor"},{"category":"Cast","name":"Actor Two"},{"category":"director","name":"Director"},{"category":"Actors","name":"Actor Four"}]""")
        assertEquals(listOf("Actor One", "Actor Two", "Actor Three", "Actor Four"), cast.map { it.name })
        assertTrue(cast.all { it.photo == null && it.character == null })
    }

    @Test fun duplicateRepresentationsFillMissingFieldsWithoutDuplicatingPeople() {
        val cast = parse(""""app_extras":{"cast":[{"name":"Actor One","photo":"file:///unsafe"}]},"credits_cast":[{"name":"Actor One","character":"Pilot","profile_path":"https://example.invalid/one.jpg"}],"cast":["Actor One","Actor Two"]""")
        assertEquals(2, cast.size)
        assertEquals("Pilot", cast[0].character)
        assertEquals("https://example.invalid/one.jpg", cast[0].photo)
    }

    @Test fun malformedOptionalCastNeverDestroysUsableTitleDetails() {
        assertTrue(parse(""""app_extras":false,"cast":{},"credits_cast":"bad","links":null""").isEmpty())
        val cast = parse(""""app_extras":{"cast":[{},false,null,{"name":99},{"name":"Actor One","photo":{},"character":12}]}""")
        assertEquals("Actor One", cast.single().name)
        assertNull(cast.single().photo); assertNull(cast.single().character)
    }

    @Test fun unsafePhotoUrlsAreNotLoadedAndNamesStillAppear() {
        val urls = listOf("file:///private/photo", "content://private/photo", "javascript:alert(1)", "https://user:password@example.invalid/photo", "https://example.invalid/photo#token", "/relative.jpg")
        val cast = parse(""""cast":[${urls.mapIndexed { i, url -> """{"name":"Actor $i","photo":"$url"}""" }.joinToString()}]""")
        assertEquals(urls.size, cast.size)
        assertTrue(cast.all { it.photo == null })
    }

    @Test fun castNamesRolesAndTotalCountAreBounded() {
        val cast = parse(""""cast":[{"name":"${"x".repeat(257)}"},{"name":"Actor One","character":"${"x".repeat(513)}"},${(2..100).joinToString { "\"Actor $it\"" }}]""")
        assertEquals(60, cast.size)
        assertEquals("Actor One", cast[0].name); assertNull(cast[0].character)
        assertEquals("Actor 60", cast.last().name)
    }

    @Test fun catalogPreviewsDoNotAllocateCastOrInterpretActorLinks() {
        val media = AddonMediaParser.catalog("""{"metas":[{"id":"fixture","type":"movie","name":"Fixture","app_extras":{"cast":[{"name":"Actor","photo":"https://example.invalid/photo"}]},"cast":["Actor"],"links":[{"category":"Cast","name":"Actor"}]}]}""").items.single()
        assertTrue(media.cast.isEmpty())
        assertEquals(AddonMediaKey("movie", "fixture"), media.singleVideoKey())
    }
}
