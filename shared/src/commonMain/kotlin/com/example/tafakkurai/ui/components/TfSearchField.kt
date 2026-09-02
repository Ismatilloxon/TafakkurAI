package com.example.tafakkurai.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Shared search behavior. iOS supplies UISearchBar semantics; Android uses the app's Compose
 * visual language. Feature screens own only the query and filtered result state.
 */
@Composable
expect fun TfSearchField(
    query: String,
    onQuery: (String) -> Unit,
    placeholder: String,
    searchContentDescription: String,
    clearContentDescription: String,
    modifier: Modifier = Modifier,
    onSearch: () -> Unit = {},
)
