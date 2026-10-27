package com.jarvis.assistant.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jarvis.assistant.ui.Dimens
import com.jarvis.assistant.ui.Ink
import com.jarvis.assistant.ui.rememberReducedMotion
import kotlinx.coroutines.delay

/* ------------------------------------------------------------------ surfaces */

@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(Dimens.cardRadius)
    var m = modifier
        .clip(shape)
        .background(Ink.Card)
        .border(1.dp, Ink.Border, shape)
    if (onClick != null) {
        m = m.clickable(onClickLabel = onClickLabel, onClick = onClick)
        if (onClickLabel != null) m = m.semantics { contentDescription = onClickLabel }
    }
    Column(modifier = m.padding(contentPadding), content = content)
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        color = Ink.TextDim,
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier,
    )
}

@Composable
fun HairlineDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Ink.Border))
}

/* ------------------------------------------------------------------- buttons */

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .heightIn(min = Dimens.touch)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(28.dp))
            .background(if (enabled) Ink.Accent else Ink.Elevated)
            .clickable(enabled = enabled, onClickLabel = text, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Ink.Background, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = text,
            color = if (enabled) Ink.Background else Ink.TextDim,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .heightIn(min = Dimens.touch)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(28.dp))
            .border(1.dp, Ink.Border, androidx.compose.foundation.shape.RoundedCornerShape(28.dp))
            .clickable(enabled = enabled, onClickLabel = text, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled) Ink.Text else Ink.TextDim,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = text,
            color = if (enabled) Ink.Text else Ink.TextDim,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/**
 * The one control big enough to dominate a screen: the microphone.
 */
@Composable
fun RoundIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 56.dp,
    filled: Boolean = false,
    enabled: Boolean = true,
    showBorder: Boolean = true,
) {
    val shape = androidx.compose.foundation.shape.CircleShape
    Box(
        modifier = modifier
            .size(diameter)
            .clip(shape)
            .background(if (filled) Ink.Accent else Color.Transparent)
            .then(if (showBorder) Modifier.border(1.dp, if (enabled) Ink.Border else Ink.BorderSoft, shape) else Modifier)
            .clickable(enabled = enabled, onClickLabel = contentDescription, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (filled) Ink.Background else if (enabled) Ink.Text else Ink.TextDim,
            modifier = Modifier.size(diameter * 0.38f),
        )
    }
}

/* --------------------------------------------------------------------- bits */

@Composable
fun StatusPill(text: String, active: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
            .border(1.dp, Ink.Border, androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(5.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(if (active) Ink.Accent else Ink.TextDim),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = text.uppercase(),
            color = if (active) Ink.Text else Ink.TextDim,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = Dimens.gutter, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = Ink.Border, modifier = Modifier.size(34.dp))
        Spacer(Modifier.height(16.dp))
        Text(title, color = Ink.Text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            body,
            color = Ink.TextDim,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun ToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.touch + 8.dp)
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink.Text, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = Ink.TextDim, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Ink.Background,
                checkedTrackColor = Ink.Accent,
                checkedBorderColor = Ink.Accent,
                uncheckedThumbColor = Ink.TextDim,
                uncheckedTrackColor = Color.Transparent,
                uncheckedBorderColor = Ink.Border,
            ),
        )
    }
}

/** label on the left, value right-aligned: used for status read-outs. */
@Composable
fun KeyValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    emphasise: Boolean = false,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Ink.TextDim, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            value,
            color = if (emphasise) Ink.Text else Ink.Text,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
        )
    }
}

/**
 * A tappable row: title, detail, a small status word and an optional trailing button.
 */
@Composable
fun ActionRow(
    title: String,
    detail: String,
    status: String?,
    statusOk: Boolean,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = Dimens.touch + 12.dp).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink.Text, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(2.dp))
            Text(detail, color = Ink.TextDim, style = MaterialTheme.typography.bodyMedium)
            if (status != null) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(5.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(if (statusOk) Ink.Accent else Ink.TextDim),
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        status.uppercase(),
                        color = if (statusOk) Ink.Text else Ink.TextDim,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        when {
            trailing != null -> trailing()
            actionLabel != null && onAction != null -> GhostButton(actionLabel, onAction)
        }
    }
}

/* --------------------------------------------------------------------- input */

@Composable
fun JarvisTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    onSend: (() -> Unit)? = null,
    contentDescription: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    Row(
        modifier = modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
            .background(Ink.Card)
            .border(1.dp, Ink.Border, androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .heightIn(min = Dimens.touch),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = textStyle.copy(color = Ink.Text),
            cursorBrush = SolidColor(Ink.Text),
            maxLines = maxLines,
            keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend?.invoke() }),
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 12.dp)
                .then(
                    if (contentDescription != null) {
                        Modifier.semantics { this.contentDescription = contentDescription }
                    } else Modifier
                ),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(placeholder, color = Ink.TextDim, style = textStyle)
                }
                inner()
            },
        )
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

/* ------------------------------------------------------------------ motion */

/**
 * Small entrance used when a screen or a list appears.
 * Skipped entirely when the system asks for reduced motion.
 */
@Composable
fun FadeIn(
    delayMillis: Int = 0,
    content: @Composable () -> Unit,
) {
    val reduced = rememberReducedMotion()
    var shown by remember { mutableStateOf(reduced) }
    LaunchedEffect(Unit) {
        if (!reduced) {
            delay(delayMillis.toLong())
            shown = true
        }
    }
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(tween(if (reduced) 0 else 340)) +
            slideInVertically(tween(if (reduced) 0 else 340)) { it / 14 },
    ) {
        content()
    }
}

/** Kept so callers do not have to import LocalTextStyle everywhere. */
@Composable
fun defaultBody(): TextStyle = LocalTextStyle.current
