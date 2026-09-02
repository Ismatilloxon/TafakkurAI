package com.example.tafakkurai.ui.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import androidx.compose.ui.unit.dp
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.*
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
private class SearchDelegate : NSObject(), UISearchBarDelegateProtocol {
    var onQuery: (String) -> Unit = {}
    var onSearch: () -> Unit = {}

    override fun searchBarTextDidBeginEditing(searchBar: UISearchBar) {
        // Match SwiftUI `.searchable`: Cancel is tied to focus, not to whether text happens
        // to be non-empty. Clearing the query while typing must not make it disappear.
        searchBar.showsCancelButton = true
    }

    override fun searchBarTextDidEndEditing(searchBar: UISearchBar) {
        searchBar.showsCancelButton = false
    }

    override fun searchBar(searchBar: UISearchBar, textDidChange: String) {
        onQuery(textDidChange)
    }

    override fun searchBarSearchButtonClicked(searchBar: UISearchBar) {
        searchBar.resignFirstResponder()
        onSearch()
    }

    override fun searchBarCancelButtonClicked(searchBar: UISearchBar) {
        searchBar.text = ""
        searchBar.resignFirstResponder()
        searchBar.showsCancelButton = false
        onQuery("")
    }
}

/** Native iOS search field, matching the behavior users expect from SwiftUI `.searchable`. */
@OptIn(ExperimentalForeignApi::class)
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
    val delegate = remember { SearchDelegate() }
    delegate.onQuery = onQuery
    delegate.onSearch = onSearch

    UIKitView(
        factory = {
            UISearchBar().apply {
                this.delegate = delegate
            }
        },
        modifier = modifier.heightIn(min = 44.dp),
        update = { searchBar ->
            if (searchBar.text != query) searchBar.text = query
            searchBar.placeholder = placeholder
            searchBar.accessibilityLabel = searchContentDescription
        },
        onRelease = { searchBar -> searchBar.delegate = null },
    )
}
