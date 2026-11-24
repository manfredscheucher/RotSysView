package org.bytefred.rotsysview.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp

/** −/+ buttons of a [CompactNumberField]: what they do and whether they are enabled. */
class Steppers(val minus: () -> Unit, val minusEnabled: Boolean, val plus: () -> Unit, val plusEnabled: Boolean)

/**
 * A compact number input as ktoy's steps/sec field: a [BasicTextField] with a 1 dp grey rounded
 * border (red while the text is invalid), as high as the buttons ([ButtonDefaults.MinHeight]), in
 * the mono style, the text vertically centred, as wide as [maxChars] characters, with "−" / "+"
 * buttons on either side if [steppers] are given. [onText] gets the [parse]d text, null while it is
 * invalid; then [error] shows in small text below. A new [value] from outside (a button, Reset)
 * replaces the text unless the text already means it, so typing `1e-5` is not rewritten on the way.
 */
@Composable
fun <T : Any> CompactNumberField(
    state: AppState,
    value: String,
    parse: (String) -> T?,
    error: String,
    maxChars: Int,
    tag: String,
    steppers: Steppers? = null,
    onText: (T?) -> Unit,
) {
    var text by remember { mutableStateOf(value) }
    LaunchedEffect(value) { if (parse(text) != parse(value)) text = value }
    val invalid = parse(text) == null
    val style = MonoStyle.copy(color = MaterialTheme.colorScheme.onSurface)
    val measurer = rememberTextMeasurer()
    val width = with(LocalDensity.current) { measurer.measure("0".repeat(maxChars), style).size.width.toDp() } + FIELD_PADDING * 2 + 2.dp
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // "−" and "+" as text (no icon dependency), like ktoy
            if (steppers != null) OutlinedButton(
                onClick = steppers.minus, modifier = STEPPER_SIZE, enabled = steppers.minusEnabled, contentPadding = STEPPER_PADDING,
            ) { Text("−") }
            BasicTextField(
                value = text,
                onValueChange = { v ->
                    text = v.take(MAX_FIELD_LENGTH)
                    onText(parse(text))
                },
                singleLine = true,
                textStyle = style,
                modifier = Modifier.width(width).height(ButtonDefaults.MinHeight).testTag(tag).textFocus(state),
                decorationBox = { inner ->
                    Box(
                        Modifier.fillMaxSize()
                            .border(1.dp, if (invalid) MaterialTheme.colorScheme.error else FIELD_BORDER, RoundedCornerShape(4.dp))
                            .padding(horizontal = FIELD_PADDING),
                        contentAlignment = Alignment.CenterStart,
                    ) { inner() }
                },
            )
            if (steppers != null) OutlinedButton(
                onClick = steppers.plus, modifier = STEPPER_SIZE, enabled = steppers.plusEnabled, contentPadding = STEPPER_PADDING,
            ) { Text("+") }
        }
        if (invalid) Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

/** At most this many characters are taken in a number field. */
internal const val MAX_FIELD_LENGTH = 12

private val FIELD_PADDING = 6.dp

/** The −/+ buttons with little padding (ktoy's compact buttons), not Material's 24 dp. */
private val STEPPER_PADDING = PaddingValues(horizontal = 8.dp)

/** Square −/+ buttons (Material's minimum width is 58 dp). */
private val STEPPER_SIZE = Modifier.defaultMinSize(minWidth = ButtonDefaults.MinHeight)

/** ktoy's field border. */
private val FIELD_BORDER = Color(0xFF888888)
