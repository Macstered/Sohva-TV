package com.sohva.tv.feature.organize

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sohva.tv.core.data.org.ManagedGroup
import com.sohva.tv.core.data.org.ManagedKind
import com.sohva.tv.core.model.org.OrgKeys
import com.sohva.tv.ui.design.R
import com.sohva.tv.ui.design.components.LogoTile
import com.sohva.tv.ui.design.focus.SurfaceState
import com.sohva.tv.ui.design.focus.SurfaceStyle
import com.sohva.tv.ui.design.focus.TvSurface
import com.sohva.tv.ui.design.text.Text
import com.sohva.tv.ui.design.theme.Sohva

/** The mark before a row's title (ORG-FR-43): selected in selection mode, on, or off. */
enum class RowMark { SELECTED, ON, OFF }

/**
 * A manager row (design/03 §5.3): 57 dp, medium corners, `surface` at rest; focus uses the
 * standard fill flip (spec 42 Q1), not beta 23's orange border. Mark, an optional 40 dp image,
 * title over subtitle. A row being moved shows as selected.
 */
@Composable
internal fun ManagerRow(
    mark: RowMark,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    image: String? = null,
    imageName: String? = null,
    dimmed: Boolean = false,
    moving: Boolean = false,
) {
    val palette = Sohva.palette
    val style = SurfaceStyle(corner = Sohva.shapes.medium, resting = palette.surface, focusScale = 1f, padding = PaddingValues(horizontal = 10.dp))
    TvSurface(onClick = onClick, modifier = modifier.fillMaxWidth().height(57.dp), state = SurfaceState(selected = moving), style = style) { colors ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            val (glyph, tint) = when (mark) {
                RowMark.SELECTED -> "●" to palette.accent
                RowMark.ON -> "✓" to palette.accent
                RowMark.OFF -> "—" to palette.textMuted
            }
            Text(glyph, Modifier.width(16.dp), style = Sohva.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = tint)
            if (imageName != null) LogoTile(imageName, image, 40.dp, fontSize = Sohva.typography.caption.fontSize)
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = Sohva.typography.label.copy(fontWeight = FontWeight.SemiBold),
                    color = if (dimmed) palette.textMuted else colors.content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // 12 sp, not beta 23's 10 sp, which is below the type floor (spec 42 §5.1).
                Text(subtitle, style = Sohva.typography.caption, color = palette.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A group row's label: the shortcut's or list's name, the provider group's, or "Ungrouped". */
@Composable
internal fun groupLabel(group: ManagedGroup): String = when (group.key) {
    OrgKeys.HISTORY -> stringResource(R.string.manager_history)
    OrgKeys.FAVOURITES -> stringResource(R.string.manager_favourites)
    OrgKeys.RECENT -> stringResource(R.string.manager_recent)
    else -> group.label.ifBlank { stringResource(R.string.manager_ungrouped) }
}

/** "Automatic view" for shortcuts, else "enabled / total" (ORG-FR-43). */
@Composable
internal fun groupSubtitle(group: ManagedGroup): String =
    if (group.kind == ManagedKind.SHORTCUT) stringResource(R.string.manager_automatic_view) else "${group.enabled} / ${group.total}"
