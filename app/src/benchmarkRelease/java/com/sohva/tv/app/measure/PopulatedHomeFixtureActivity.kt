package com.sohva.tv.app.measure

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import com.sohva.tv.app.SohvaApplication
import com.sohva.tv.core.model.home.HomeLayout
import com.sohva.tv.core.model.profile.Household
import com.sohva.tv.core.model.settings.StartupScreen
import com.sohva.tv.feature.trakt.protocol.TraktIds
import com.sohva.tv.feature.trakt.protocol.TraktKind
import com.sohva.tv.feature.trakt.shelf.TraktCard
import com.sohva.tv.feature.trakt.shelf.TraktRowSource
import com.sohva.tv.feature.trakt.shelf.TraktShelf
import java.io.File
import kotlinx.coroutines.runBlocking

/** Eight stored rows × 30 unique titles, over the owner-scale library seeded by FixtureActivity. */
class PopulatedHomeFixtureActivity : Activity() {
    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val label = TextView(this).apply { text = "fixture-busy" }
        setContentView(label)
        val populated = intent.getBooleanExtra("populated", true)
        Thread({
            val graph = (application as SohvaApplication).graph
            runBlocking(graph.dispatchers.io) {
                val prefs = graph.data.preferences
                prefs.editHousehold { Household() }
                prefs.setStartupScreen(StartupScreen.HOME)
                val trakt = checkNotNull(graph.trakt)
                trakt.store.clearAll()
                trakt.rowLists.clear()
                trakt.shelves.clear()
                var layout = HomeLayout.DEFAULT
                if (populated) {
                    val art = HomeFixtureArtwork(File(filesDir, "benchmark-home-art-v1"))
                    val now = System.currentTimeMillis()
                    val added = (0 until HomeLayout.MAX_ADDED).map { row ->
                        val source = TraktRowSource.list(FIRST_LIST + row)
                        val cards = (0 until TraktRowSource.TITLES).map { column ->
                            val index = row * TraktRowSource.TITLES + column
                            TraktCard(
                                kind = TraktKind.MOVIE, ids = TraktIds(trakt = FIRST_TITLE + index, tmdb = FIRST_TITLE + index),
                                title = "Fictional film $index", year = 2000 + index % 26,
                                overview = "A fictional story for the populated Home performance fixture. ".repeat(8),
                                poster = art.poster(index), fanart = art.backdrop(index),
                            )
                        }
                        trakt.rowLists.save("default", source, TraktShelf(now, cards, "Fictional row ${row + 1}"))
                        // Half of the cards exercise the indexed library marks too.
                        graph.data.database.runInTransaction {
                            cards.forEachIndexed { column, card ->
                                if (column % 2 == 0) graph.data.database.openHelper.writableDatabase.execSQL(
                                    "UPDATE movie SET work_key = ? WHERE key = ?",
                                    arrayOf("tmdb:${card.ids.tmdb}", "vod:movie:owner-vod:${row * 30 + column}"),
                                )
                            }
                        }
                        layout = layout.withAdded(source.id)
                        source.id
                    }
                    layout = layout.withOrder(listOf(HomeLayout.CONTINUE) + added)
                }
                prefs.setHomeLayout("default", layout)
                Log.i("SohvaFixture", "populated=$populated rows=${layout.added.size} titles=${layout.added.size * 30} heroLowMemory=${graph.player.lowMemory}")
            }
            runOnUiThread {
                label.text = "fixture-ready"
                label.postDelayed({ finish() }, 300)
            }
        }, "home-fixture").apply { priority = Thread.MIN_PRIORITY }.start()
    }

    private companion object {
        const val FIRST_LIST = 810_000L
        const val FIRST_TITLE = 910_000L
    }
}
