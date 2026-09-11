package com.sohva.tv.addons

/** Synthetic capability-shaped fixture; contains no exported user configuration. */
internal fun aioManifest(name: String = "Metadata fixture", required: Boolean = false): String = """
    {
      "id":"test.metadata", "version":"1.0.0", "name":"$name",
      "types":["movie","series","anime.series","Trakt","collection"],
      "idPrefixes":["tt","tmdb:","tvdb:","kitsu:"],
      "resources":["catalog","meta",{"name":"stream","types":["movie"]}],
      "catalogs":[
        {"id":"trending","type":"movie","name":"Trending","pageSize":20,"extra":[{"name":"skip"},{"name":"search"}]},
        {"id":"anime","type":"anime.series","pageSize":25,"extra":[{"name":"skip"}]},
        {"id":"mine","type":"Trakt","showInHome":false,"extra":[{"name":"genre","isRequired":true,"options":["Drama","Science Fiction"]},{"name":"skip"}]}
      ],
      "behaviorHints":{"configurable":true,"configurationRequired":$required},
      "unknownFutureField":{"allowed":true}
    }
""".trimIndent()

internal inline fun expectFailure(expected: AddonFailure, block: () -> Unit): AddonException {
    try { block() } catch (error: AddonException) {
        org.junit.Assert.assertEquals(expected, error.failure)
        org.junit.Assert.assertNull(error.cause)
        return error
    }
    throw AssertionError("Expected $expected")
}
