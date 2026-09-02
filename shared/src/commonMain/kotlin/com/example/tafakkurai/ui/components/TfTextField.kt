package com.example.tafakkurai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tafakkurai.ui.Fonts
import com.example.tafakkurai.ui.LocalTf
import com.example.tafakkurai.ui.TfText

/** Reusable app text field with one visual, keyboard and placeholder behavior. */
@Composable
fun TfTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {},
) {
    val c = LocalTf.current
    Box(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.card2)
            .border(0.5.dp, c.line, RoundedCornerShape(12.dp))
            .padding(horizontal = 13.dp, vertical = 12.dp),
    ) {
        if (value.isEmpty()) TfText(placeholder, color = c.fg3, size = 14.5f)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = TextStyle(color = c.fg, fontSize = 14.5.sp, fontFamily = Fonts.sans),
            cursorBrush = SolidColor(c.accent),
            keyboardOptions = KeyboardOptions(imeAction = imeAction),
            keyboardActions = KeyboardActions(
                onDone = { onImeAction() },
                onGo = { onImeAction() },
                onSend = { onImeAction() },
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
