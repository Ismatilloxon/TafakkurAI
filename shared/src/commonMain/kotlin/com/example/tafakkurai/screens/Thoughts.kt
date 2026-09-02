package com.example.tafakkurai.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.tafakkurai.SocialPost
import com.example.tafakkurai.i18n.LocalStrings
import com.example.tafakkurai.i18n.formatJournalDate
import com.example.tafakkurai.i18n.tr
import com.example.tafakkurai.social.domain.MAX_SOCIAL_POST_LENGTH
import com.example.tafakkurai.social.domain.SocialConnection
import com.example.tafakkurai.social.domain.SocialConnectionSummary
import com.example.tafakkurai.social.domain.SocialRepository
import com.example.tafakkurai.social.presentation.SocialMutationResult
import com.example.tafakkurai.social.presentation.SocialStateHolder
import com.example.tafakkurai.ui.*
import com.example.tafakkurai.ui.components.TfActionButton
import com.example.tafakkurai.ui.components.TfConfirmationDialog
import com.example.tafakkurai.ui.components.TfTextField

@Composable
fun ThoughtsScreen(
    posts: List<SocialPost>,
    store: SocialRepository,
    loading: Boolean,
    loadingMore: Boolean,
    hasMore: Boolean,
    errorKey: String?,
    loadMoreErrorKey: String?,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onCompose: () -> Unit,
    onLike: (String) -> Unit,
) {
    val c = LocalTf.current
    val stateHolder = remember(store) { SocialStateHolder(store) }
    val socialRevision = stateHolder.revision
    val socialState = remember(socialRevision) { stateHolder.state() }
    var actionErrorKey by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        LargeTitle(tr("thoughts.title"), right = {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(c.accent.soft(0.16f))
                    .then(pressScale(onCompose, 0.9f)),
                contentAlignment = Alignment.Center,
            ) {
                TfIcon("plus", size = 20, color = c.accent)
            }
        })
        ScrollBody {
            if (socialState.loadFailed || actionErrorKey != null) {
                InsetCard {
                    TfText(
                        tr(if (socialState.loadFailed) "social.load_failed" else actionErrorKey!!),
                        color = c.rose,
                        size = 13.5f,
                        lineHeight = 1.45f,
                    )
                    TfActionButton(
                        label = tr("common.retry"),
                        onClick = {
                            actionErrorKey = null
                            stateHolder.refresh()
                        },
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = HPAD.dp)
                    .clip(RoundedCornerShape(14.dp)).background(c.card)
                    .border(0.5.dp, c.sep, RoundedCornerShape(14.dp))
                    .then(pressScale(onCompose, 0.985f)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                UserAvatar(size = 32)
                TfText(tr("thoughts.compose_hint"), color = c.fg3, size = 14.5f, modifier = Modifier.weight(1f))
                TfIcon("penLine", size = 18, color = c.fg3)
            }

            val joined = socialState.circles.filter { it.joined }.map { it.circle }
            if (joined.isNotEmpty()) {
                Overline(tr("thoughts.circles"), color = c.fg3, modifier = Modifier.padding(start = 20.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .padding(start = 20.dp, end = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    joined.forEach { circle ->
                        Row(
                            Modifier.clip(RoundedCornerShape(999.dp)).background(c.card2)
                                .border(0.5.dp, c.sep, RoundedCornerShape(999.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            TfIcon(circle.icon, size = 15, color = c.green)
                            TfText(tr(circle.titleKey), color = c.fg, size = 13f, weight = FontWeight.Medium)
                        }
                    }
                }
            }

            if (loading && posts.isEmpty()) {
                InsetCard {
                    TfText(tr("thoughts.loading"), color = c.fg2, size = 13.5f)
                }
            } else if (errorKey != null && posts.isEmpty()) {
                InsetCard {
                    TfText(tr(errorKey), color = c.rose, size = 13.5f, lineHeight = 1.45f)
                    TfActionButton(
                        label = tr("common.retry"),
                        onClick = onRetry,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            } else if (posts.isEmpty()) {
                InsetCard {
                    TfIcon("messageCircle", size = 23, color = c.fg3)
                    TfText(
                        tr("thoughts.empty"),
                        color = c.fg2,
                        size = 13.5f,
                        lineHeight = 1.45f,
                        modifier = Modifier.padding(top = 9.dp),
                    )
                }
            } else {
                if (errorKey != null) {
                    InsetCard {
                        TfText(tr(errorKey), color = c.rose, size = 13.5f, lineHeight = 1.45f)
                        TfActionButton(
                            label = tr("common.retry"),
                            onClick = onRetry,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                }
                Column(
                    Modifier.padding(horizontal = HPAD.dp, vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    posts.forEach { post ->
                        PostCard(
                            p = post,
                            stateHolder = stateHolder,
                            onLike = onLike,
                            onStorageError = { actionErrorKey = "social.save_failed" },
                        )
                    }
                    if (loadMoreErrorKey != null) {
                        Card(Modifier.fillMaxWidth()) {
                            TfText(tr(loadMoreErrorKey), color = c.rose, size = 13.5f, lineHeight = 1.45f)
                            TfActionButton(
                                label = tr("common.retry"),
                                onClick = onLoadMore,
                                enabled = !loadingMore && !loading,
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        }
                    } else if (hasMore) {
                        TfActionButton(
                            label = tr(if (loadingMore) "common.loading" else "common.continue"),
                            onClick = onLoadMore,
                            enabled = !loadingMore && !loading,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PostCard(
    p: SocialPost,
    stateHolder: SocialStateHolder,
    onLike: (String) -> Unit,
    onStorageError: () -> Unit,
) {
    val c = LocalTf.current
    val preference = stateHolder.postPreference(p.id)
    val liked = p.likedByMe
    val visibleLikeCount = p.likes.coerceAtLeast(0)
    Card(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Avatar(p.author, p.avatarSeed, size = 38)
            Column(Modifier.weight(1f)) {
                TfText(p.author, color = c.fg, size = 14.5f, weight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (p.handle.isNotBlank()) TfText(p.handle, color = c.fg3, size = 12.5f)
                    if (p.time.isNotBlank()) TfText("· ${p.time}", color = c.fg3, size = 12.5f)
                }
            }
        }
        TfText(p.text, color = c.fg, size = 14.5f, lineHeight = 1.6f, modifier = Modifier.padding(top = 11.dp))
        Row(
            Modifier.fillMaxWidth().padding(top = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActionStat(
                icon = "heart",
                count = visibleLikeCount,
                color = if (liked) c.rose else c.fg3,
                contentDescription = if (liked) tr("thoughts.unlike") else tr("thoughts.like"),
            ) {
                onLike(p.id)
            }
            Spacer(Modifier.weight(1f))
            val bookmarkLabel = if (preference.bookmarked) {
                tr("thoughts.remove_bookmark")
            } else {
                tr("thoughts.bookmark")
            }
            Row(
                Modifier.heightIn(min = 44.dp).then(pressScale({
                    if (
                        stateHolder.setPostBookmarked(p.id, !preference.bookmarked) !=
                        SocialMutationResult.SUCCESS
                    ) {
                        onStorageError()
                    }
                }, 0.9f)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TfIcon("bookmark", size = 17, color = if (preference.bookmarked) c.gold else c.fg3)
                TfText(bookmarkLabel, color = c.fg3, size = 11.5f)
            }
        }
    }
}

@Composable
private fun ActionStat(
    icon: String,
    count: Int,
    color: Color,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val c = LocalTf.current
    Row(
        Modifier.heightIn(min = 44.dp).then(pressScale(onClick, 0.9f)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TfIcon(icon, size = 17, color = color)
        TfText("$count", color = c.fg3, size = 12.5f, family = Fonts.mono)
        // The label is visible as well as available to assistive technology.
        TfText(contentDescription, color = c.fg3, size = 11.5f)
    }
}

@Composable
fun ComposeThoughtScreen(
    publishing: Boolean,
    errorKey: String?,
    onBack: () -> Unit,
    onPublish: (String) -> Unit,
) {
    val c = LocalTf.current
    var text by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        BackBar(tr("thoughts.new_title"), onBack, right = {
            val enabled = text.isNotBlank() && !publishing
            Box(
                Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(999.dp))
                    .background(if (enabled) c.accent else c.card3)
                    .then(if (enabled) pressScale({
                        onPublish(text.trim())
                    }, 0.92f) else Modifier)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                TfText(
                    tr(if (publishing) "thoughts.publishing" else "thoughts.publish"),
                    color = if (enabled) Color.White else c.fg3,
                    size = 14f,
                    weight = FontWeight.SemiBold,
                )
            }
        })
        ScrollBody {
            InsetCard(pad = 16) {
                TfTextField(
                    value = text,
                    onValueChange = { text = it.take(MAX_SOCIAL_POST_LENGTH) },
                    placeholder = tr("thoughts.editor_hint"),
                    singleLine = false,
                    modifier = Modifier.heightIn(min = 150.dp),
                )
                TfText(
                    tr("thoughts.character_count", text.length, MAX_SOCIAL_POST_LENGTH),
                    color = c.fg3,
                    size = 11.5f,
                    family = Fonts.mono,
                    modifier = Modifier.align(Alignment.End).padding(top = 8.dp),
                )
                errorKey?.let { key ->
                    TfText(
                        tr(key),
                        color = c.rose,
                        size = 12.5f,
                        lineHeight = 1.4f,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
            PrivacyFooter(tr("thoughts.publish_privacy"))
        }
    }
}

/** Circles and private relationship check-ins; no fabricated people or activity claims. */
@Composable
fun SocialScreen(store: SocialRepository, onBack: () -> Unit) {
    val c = LocalTf.current
    val stateHolder = remember(store) { SocialStateHolder(store) }
    val socialRevision = stateHolder.revision
    val uiState = remember(socialRevision) { stateHolder.state() }
    var tab by remember { mutableStateOf("circles") }
    var connectionName by remember { mutableStateOf("") }
    var relationship by remember { mutableStateOf("family") }
    var actionErrorKey by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<SocialConnection?>(null) }
    var showAllConnections by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        BackBar(tr("social.title"), onBack)
        Box(Modifier.padding(horizontal = HPAD.dp, vertical = 10.dp)) {
            Segmented(
                listOf(
                    "circles" to tr("social.tab.circles"),
                    "connections" to tr("social.tab.connections"),
                ),
                tab,
                { tab = it },
            )
        }
        ScrollBody {
            if (uiState.loadFailed || actionErrorKey != null) {
                InsetCard {
                    TfText(
                        tr(if (uiState.loadFailed) "social.load_failed" else actionErrorKey!!),
                        color = c.rose,
                        size = 13.5f,
                        lineHeight = 1.45f,
                    )
                    TfActionButton(
                        label = tr("common.retry"),
                        onClick = {
                            actionErrorKey = null
                            stateHolder.refresh()
                        },
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
            if (tab == "circles") {
                InsetCard {
                    TfText(tr("social.circles_intro"), color = c.fg2, size = 13.5f, lineHeight = 1.45f)
                }
                Column(
                    Modifier.padding(horizontal = HPAD.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    uiState.circles.forEach { circleState ->
                        val circle = circleState.circle
                        val joined = circleState.joined
                        Card(Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                IconBadge(circle.icon, c.named("cyan"), size = 44)
                                Column(Modifier.weight(1f)) {
                                    TfText(tr(circle.titleKey), color = c.fg, size = 15.5f, weight = FontWeight.SemiBold)
                                    TfText(tr(circle.descriptionKey), color = c.fg2, size = 12.5f, lineHeight = 1.35f)
                                }
                                Box(
                                    Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(999.dp))
                                        .background(if (joined) c.green.soft(0.16f) else c.accent.soft(0.16f))
                                        .then(pressScale({
                                            if (
                                                stateHolder.setCircleJoined(circle.id, !joined) !=
                                                SocialMutationResult.SUCCESS
                                            ) {
                                                actionErrorKey = "social.save_failed"
                                            }
                                        }, 0.95f))
                                        .padding(horizontal = 14.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    TfText(
                                        if (joined) tr("social.leave") else tr("social.join"),
                                        color = if (joined) c.green else c.accent,
                                        size = 13f,
                                        weight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                    }
                }
                PrivacyFooter(tr("social.circle_privacy"))
            } else {
                InsetCard {
                    Overline(tr("social.add_connection"))
                    TfTextField(
                        value = connectionName,
                        onValueChange = { connectionName = it.take(80); actionErrorKey = null },
                        placeholder = tr("social.connection_name_hint"),
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    RelationshipPicker(relationship, { relationship = it }, Modifier.padding(top = 10.dp))
                    TfActionButton(
                        label = tr("social.save_connection"),
                        onClick = {
                            when (stateHolder.addConnection(connectionName, relationship)) {
                                SocialMutationResult.SUCCESS -> {
                                    actionErrorKey = null
                                    connectionName = ""
                                }
                                SocialMutationResult.INVALID_NAME -> {
                                    actionErrorKey = "social.connection_invalid"
                                }
                                SocialMutationResult.SAVE_FAILED -> {
                                    actionErrorKey = "social.save_failed"
                                }
                            }
                        },
                        enabled = connectionName.isNotBlank(),
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }

                SectionTitle(tr("social.connections"))
                val summaries = uiState.connections
                if (summaries.isEmpty()) {
                    InsetCard {
                        TfText(tr("social.connections_empty"), color = c.fg2, size = 13.5f, lineHeight = 1.45f)
                    }
                } else {
                    Column(
                        Modifier.padding(horizontal = HPAD.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        val visibleConnections = if (showAllConnections) {
                            summaries
                        } else {
                            summaries.take(MAX_VISIBLE_CONNECTIONS)
                        }
                        visibleConnections.forEach { summary ->
                            ConnectionCard(
                                summary = summary,
                                todayKey = uiState.currentDateKey,
                                onCheckIn = {
                                    actionErrorKey = if (
                                        stateHolder.checkIn(summary.connection.id) !=
                                        SocialMutationResult.SUCCESS
                                    ) {
                                        "social.save_failed"
                                    } else {
                                        null
                                    }
                                },
                                onDelete = {
                                    actionErrorKey = null
                                    pendingDelete = summary.connection
                                },
                            )
                        }
                        if (!showAllConnections && summaries.size > MAX_VISIBLE_CONNECTIONS) {
                            TfActionButton(
                                label = tr("common.continue"),
                                onClick = { showAllConnections = true },
                            )
                        }
                    }
                }
                PrivacyFooter(tr("social.connections_privacy"))
            }
        }
    }

    pendingDelete?.let { connection ->
        TfConfirmationDialog(
            title = tr("social.delete.title"),
            message = tr("social.delete.message", connection.name),
            confirmLabel = tr("social.delete.confirm"),
            cancelLabel = tr("common.cancel"),
            onConfirm = {
                actionErrorKey = if (
                    stateHolder.deleteConnection(connection) == SocialMutationResult.SUCCESS
                ) {
                    null
                } else {
                    "social.save_failed"
                }
                pendingDelete = null
            },
            onCancel = { pendingDelete = null },
        )
    }
}

@Composable
private fun RelationshipPicker(selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalTf.current
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        RELATIONSHIP_IDS.forEach { id ->
            val active = selected == id
            Box(
                Modifier.clip(RoundedCornerShape(999.dp))
                    .background(if (active) c.accent.soft(0.18f) else c.card3)
                    .border(0.5.dp, if (active) c.accent else c.sep, RoundedCornerShape(999.dp))
                    .then(pressScale({ onSelect(id) }, 0.95f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                TfText(tr("social.relationship.$id"), color = if (active) c.accent else c.fg2, size = 12.5f)
            }
        }
    }
}

@Composable
private fun ConnectionCard(
    summary: SocialConnectionSummary,
    todayKey: String,
    onCheckIn: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = LocalTf.current
    val strings = LocalStrings.current
    val connection = summary.connection
    val checkedInToday = connection.lastContactDate == todayKey
    Card(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            Avatar(connection.name, size = 42)
            Column(Modifier.weight(1f)) {
                TfText(connection.name, color = c.fg, size = 15f, weight = FontWeight.SemiBold)
                TfText(
                    if (connection.lastContactDate.isBlank()) {
                        tr("social.never_checked_in")
                    } else {
                        tr(
                            "social.last_check_in",
                            strings.formatJournalDate(connection.lastContactDate, todayKey),
                        )
                    },
                    color = c.fg2,
                    size = 12f,
                )
            }
            TfText(
                tr("social.relationship.${connection.relationshipId}"),
                color = c.fg3,
                size = 11.5f,
            )
        }
        TfActionButton(
            label = tr(if (checkedInToday) "social.checked_in_today" else "social.check_in_today"),
            onClick = onCheckIn,
            enabled = !checkedInToday,
            modifier = Modifier.padding(top = 12.dp),
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            Row(
                Modifier.heightIn(min = 44.dp).then(pressScale(onDelete, 0.92f)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TfIcon("trash", size = 15, color = c.rose)
                TfText(tr("social.delete_connection"), color = c.rose, size = 12f)
            }
        }
    }
}

private val RELATIONSHIP_IDS = listOf("family", "friend", "mentor", "community", "other")
private const val MAX_VISIBLE_CONNECTIONS = 100
