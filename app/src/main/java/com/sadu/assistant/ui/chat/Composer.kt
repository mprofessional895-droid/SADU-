package com.sadu.assistant.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.sadu.assistant.ui.Dimens
import com.sadu.assistant.ui.Ink
import com.sadu.assistant.ui.components.SaduTextField

/**
 * The one place a message is written, on a new chat and in a conversation alike.
 *
 * The card grows with the text up to six lines, then the field scrolls inside it, so a long
 * message never pushes the card off the screen. Enter starts a new line; Send closes the
 * keyboard and clears focus so the answer has the whole screen.
 */
@Composable
fun Composer(
    draft: String,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onMic: () -> Unit,
    canStop: Boolean,
    internetOn: Boolean,
    onInternet: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val send: () -> Unit = {
        if (draft.isNotBlank()) {
            keyboard?.hide()
            focus.clearFocus(force = true)
            onSend()
        }
    }
    val shape = RoundedCornerShape(26.dp)
    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Dimens.gutter, end = Dimens.gutter, top = 6.dp, bottom = 8.dp)
            .softCard()
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        SaduTextField(
            value = draft,
            onValueChange = onDraft,
            placeholder = "Message Sadu",
            maxLines = 6,
            onSend = send,
            contentDescription = "Message",
            imeAction = ImeAction.Default,
            bare = true,
            modifier = Modifier.fillMaxWidth(),
            fieldModifier = modifier,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InternetChip(on = internetOn, onToggle = onInternet)
            Spacer(Modifier.weight(1f))
            ComposerButton(
                icon = Icons.Rounded.Mic,
                label = "Talk to Sadu",
                onClick = onMic,
            )
            Spacer(Modifier.width(8.dp))
            if (canStop) {
                ComposerButton(
                    icon = Icons.Rounded.Stop,
                    label = "Stop answering",
                    onClick = onStop,
                    filled = true,
                )
            } else {
                ComposerButton(
                    icon = Icons.AutoMirrored.Rounded.Send,
                    label = "Send",
                    onClick = send,
                    filled = draft.isNotBlank(),
                    enabled = draft.isNotBlank(),
                )
            }
        }
    }
}

@Composable
private fun ComposerButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    filled: Boolean = false,
    enabled: Boolean = true,
) {
    Icon(
        imageVector = icon,
        contentDescription = label,
        tint = if (filled) Ink.Background else Ink.Text,
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(if (filled) Ink.Text else androidx.compose.ui.graphics.Color.Transparent)
            .border(
                width = if (filled) 0.dp else 1.dp,
                color = if (enabled) Ink.Border else Ink.Border.copy(alpha = 0.4f),
                shape = RoundedCornerShape(23.dp),
            )
            .clickable(enabled = enabled, onClickLabel = label, onClick = onClick)
            .padding(12.dp),
    )
}

/**
 * A card that fades out at the top and bottom, so the composer reads as part of the page
 * rather than as a box sitting on it.
 */
private fun Modifier.softCard() = this.drawBehind {
    val radius = CornerRadius(26.dp.toPx())
    val outline = Path().apply {
        addRoundRect(RoundRect(0f, 0f, size.width, size.height, radius))
    }
    drawPath(
        path = outline,
        brush = Brush.verticalGradient(
            listOf(
                Ink.Border.copy(alpha = 0f),
                Ink.Border.copy(alpha = 0.85f),
                Ink.Border.copy(alpha = 0.3f),
            )
        ),
        style = Stroke(width = 1.dp.toPx()),
    )
    drawRoundRect(
        brush = Brush.verticalGradient(
            listOf(Ink.Card.copy(alpha = 0.94f), Ink.Card.copy(alpha = 0.58f))
        ),
        cornerRadius = radius,
    )
}

/** The Internet switch, next to the message it applies to. */
@Composable
private fun InternetChip(on: Boolean, onToggle: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(if (on) Ink.Text else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(
                onClickLabel = if (on) "Turn Internet off" else "Turn Internet on",
                onClick = { onToggle(!on) },
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Language,
            contentDescription = null,
            tint = if (on) Ink.Background else Ink.TextDim,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "Internet",
            color = if (on) Ink.Background else Ink.TextDim,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
