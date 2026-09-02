package com.example.tafakkurai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tafakkurai.ui.Fonts
import com.example.tafakkurai.ui.LocalTf
import com.example.tafakkurai.ui.TfIcon
import com.example.tafakkurai.ui.TfText
import com.example.tafakkurai.ui.pressScale

@Composable
actual fun TfSearchField(
    query: String,
    onQuery: (String) -> Unit,
    placeholder: String,
    searchContentDescription: String,
    clearContentDescription: String,
    modifier: Modifier,
    onSearch: () -> Unit,
) {
    val c = LocalTf.current
    val focus = LocalFocusManager.current

    Row(
        modifier.clip(RoundedCornerShape(12.dp)).background(c.card3)
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        TfIcon("search", size = 16, color = c.fg3)
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) TfText(placeholder, color = c.fg3, size = 14f)
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = TextStyle(color = c.fg, fontSize = 14.sp, fontFamily = Fonts.sans),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    focus.clearFocus()
                    onSearch()
                }),
                modifier = Modifier.fillMaxWidth().semantics {
                    contentDescription = searchContentDescription
                },
            )
        }
        if (query.isNotEmpty()) {
            Box(
                Modifier.size(44.dp)
                    .semantics { contentDescription = clearContentDescription }
                    .then(pressScale({ onQuery("") }, 0.9f)),
                contentAlignment = Alignment.Center,
            ) {
                TfIcon("x", size = 15, color = c.fg3)
            }
        }
    }
}
