package com.saiyanstrong.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saiyanstrong.presentation.theme.NeonGreen

/** Inline BasicTextField for KG/REPS cells — select-all on focus, NeonGreen focus border. */
@Composable
fun SetCell(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    onImeAction: () -> Unit,
    focusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val latestValue by rememberUpdatedState(value)
    val latestOnChange by rememberUpdatedState(onValueChange)
    val latestOnFocusChanged by rememberUpdatedState(onFocusChanged)

    // Select all text the moment the field gains focus so first keystroke replaces it
    LaunchedEffect(focused) {
        latestOnFocusChanged(focused)
        if (focused) {
            val v = latestValue
            latestOnChange(v.copy(selection = TextRange(0, v.text.length)))
        }
    }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .focusRequester(focusRequester)
            .border(1.dp, if (focused) NeonGreen else Color.White.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            .background(if (focused) NeonGreen.copy(alpha = 0.08f) else Color.Transparent, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 8.dp),
        textStyle = TextStyle(
            color = Color.White, fontSize = 16.sp,
            fontWeight = FontWeight.Black, textAlign = TextAlign.Center
        ),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(
            onNext = { onImeAction() },
            onDone = { onImeAction() },
            onGo = { onImeAction() }
        ),
        interactionSource = source,
        cursorBrush = SolidColor(NeonGreen)
    )
}

/** −/+ step chips shown under a focused SetCell. */
@Composable
fun StepperRow(stepLabel: String, onStep: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(-1 to "−$stepLabel", 1 to "+$stepLabel").forEach { (direction, label) ->
            Text(
                label,
                color = NeonGreen, fontSize = 13.sp, fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .background(NeonGreen.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                    .border(1.dp, NeonGreen.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                    .clickable { onStep(direction) }
                    .padding(horizontal = 18.dp, vertical = 6.dp)
            )
        }
    }
}
