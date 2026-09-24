package com.sohva.tv.app.gallery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sohva.tv.app.SohvaApplication
import com.sohva.tv.core.model.settings.ColorThemeId
import com.sohva.tv.core.model.settings.InterfaceScale
import com.sohva.tv.ui.design.components.ListRowLayout
import com.sohva.tv.ui.design.components.SettingsOverline
import com.sohva.tv.ui.design.components.TvListRow
import com.sohva.tv.ui.design.focus.SurfaceState
import com.sohva.tv.ui.design.gallery.GallerySection
import com.sohva.tv.ui.design.gallery.GallerySectionContent
import com.sohva.tv.ui.design.ground.LocalRenderDispatcher
import com.sohva.tv.ui.design.ground.ScreenBackground
import com.sohva.tv.ui.design.theme.InterfaceScaled
import com.sohva.tv.ui.design.theme.SohvaTheme

/**
 * Every shared component in every state, theme and interface size, on the TV itself (roadmap
 * M0). Debug builds only. Left column: page, theme and size; the page itself on the right.
 */
class GalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dispatchers = (application as SohvaApplication).graph.dispatchers
        setContent {
            CompositionLocalProvider(LocalRenderDispatcher provides dispatchers.ui) { Gallery() }
        }
    }
}

@Composable
private fun Gallery() {
    var section by rememberSaveable { mutableStateOf(GallerySection.TOKENS) }
    var theme by rememberSaveable { mutableStateOf(ColorThemeId.ORIGINAL) }
    var scale by rememberSaveable { mutableStateOf(InterfaceScale.NORMAL) }
    var reduced by rememberSaveable { mutableStateOf(false) }
    SohvaTheme(theme, reducedMotion = reduced) {
        InterfaceScaled(scale) {
            ScreenBackground {
                Row(Modifier.fillMaxSize()) {
                    Column(Modifier.width(220.dp).padding(start = 16.dp, top = 8.dp).verticalScroll(rememberScrollState())) {
                        Chooser("Page", GallerySection.entries, section, { it.name }) { section = it }
                        Chooser("Theme", ColorThemeId.entries, theme, { it.id }) { theme = it }
                        Chooser("Size", InterfaceScale.entries, scale, { it.name }) { scale = it }
                        Chooser("Motion", listOf(false, true), reduced, { if (it) "Reduced" else "Standard" }) { reduced = it }
                        Spacer(Modifier.height(24.dp))
                    }
                    Box(Modifier.weight(1f).padding(vertical = 24.dp)) { GallerySectionContent(section) }
                }
            }
        }
    }
}

@Composable
private fun <T> Chooser(title: String, options: List<T>, current: T, label: (T) -> String, onPick: (T) -> Unit) {
    SettingsOverline(title)
    options.forEach { option ->
        TvListRow(
            label = label(option),
            onClick = { onPick(option) },
            state = SurfaceState(selected = option == current),
            layout = ListRowLayout(dense = true),
        )
    }
}
