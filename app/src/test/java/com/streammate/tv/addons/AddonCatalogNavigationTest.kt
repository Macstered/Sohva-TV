package com.streammate.tv.addons

import com.sohva.tv.addons.AddonManifestParser
import org.junit.Assert.assertEquals
import org.junit.Test

class AddonCatalogNavigationTest {
    @Test fun onlyRequiredFilterCatalogsStayOffLandingIncludingLegacyDeclarations() {
        val manifest = AddonManifestParser.parse("""{
            "id":"test.navigation","version":"1","name":"Navigation fixture",
            "types":["movie"],"resources":["catalog"],"catalogs":[
                {"id":"plain","type":"movie"},
                {"id":"paged","type":"movie","extra":[{"name":"skip"}]},
                {"id":"optional","type":"movie","extra":[{"name":"genre","options":["Drama"]}]},
                {"id":"required","type":"movie","extra":[{"name":"genre","isRequired":true}]},
                {"id":"search","type":"movie","extra":[{"name":"search"}]},
                {"id":"legacy","type":"movie","extraSupported":["skip","genre"]},
                {"id":"legacyRequired","type":"movie","extraRequired":["genre"]},
                {"id":"hidden","type":"movie","showInHome":false}
            ]}
        """)
        assertEquals(listOf("plain", "paged", "optional", "search", "legacy"), manifest.catalogs.filter { it.belongsOnLanding() }.map { it.id })
    }
}
