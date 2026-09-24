package com.sohva.tv.ui.design.text

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.sohva.tv.ui.design.theme.LocalContentColor
import com.sohva.tv.ui.design.theme.LocalTextStyle

/**
 * Text in the content colour of its container. Styles come from `Sohva.typography`; the default
 * is the inherited style of design/01 §8 (16/24 sp, 0.5 sp tracking).
 */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Ellipsis,
) {
    val resolved = color.takeOrElse { style.color.takeOrElse { LocalContentColor.current } }
    BasicText(text = text, modifier = modifier, style = style.copy(color = resolved), overflow = overflow, maxLines = maxLines)
}

/** Rich text, for the brand lock-ups' two-colour spans. */
@Composable
fun Text(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    maxLines: Int = Int.MAX_VALUE,
) {
    val resolved = style.color.takeOrElse { LocalContentColor.current }
    BasicText(text = text, modifier = modifier, style = style.copy(color = resolved), maxLines = maxLines)
}

/**
 * Text whose colour is read in the draw phase, so an animated colour redraws the text without
 * recomposing or re-measuring it (design/01 §16.1 rule 8).
 */
@Composable
fun DrawColorText(
    text: String,
    color: ColorProducer,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    maxLines: Int = 1,
) {
    BasicText(text = text, modifier = modifier, style = style, overflow = TextOverflow.Ellipsis, maxLines = maxLines, color = color)
}
