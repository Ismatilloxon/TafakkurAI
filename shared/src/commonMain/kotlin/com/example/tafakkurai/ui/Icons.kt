package com.example.tafakkurai.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import tafakkurai.shared.generated.resources.*

/**
 * Lucide-style stroke icons. Maps the prototype's camelCase icon keys (ICONS in kit.jsx /
 * ZK_ICONS in shared.jsx) onto the bundled vector drawables, tinted at render time.
 */
private val iconMap: Map<String, DrawableResource> = mapOf(
    "home" to Res.drawable.ic_home,
    "sparkles" to Res.drawable.ic_sparkles,
    "brain" to Res.drawable.ic_brain,
    "book" to Res.drawable.ic_book,
    "bookOpen" to Res.drawable.ic_book_open,
    "moon" to Res.drawable.ic_moon,
    "moonStars" to Res.drawable.ic_moon_stars,
    "layoutGrid" to Res.drawable.ic_layout_grid,
    "checkSquare" to Res.drawable.ic_check_square,
    "briefcase" to Res.drawable.ic_briefcase,
    "users" to Res.drawable.ic_users,
    "wallet" to Res.drawable.ic_wallet,
    "sun" to Res.drawable.ic_sun,
    "sunrise" to Res.drawable.ic_sunrise,
    "sunset" to Res.drawable.ic_sunset,
    "target" to Res.drawable.ic_target,
    "activity" to Res.drawable.ic_activity,
    "code" to Res.drawable.ic_code,
    "terminal" to Res.drawable.ic_terminal,
    "languages" to Res.drawable.ic_languages,
    "sigma" to Res.drawable.ic_sigma,
    "lineChart" to Res.drawable.ic_line_chart,
    "heart" to Res.drawable.ic_heart,
    "leaf" to Res.drawable.ic_leaf,
    "server" to Res.drawable.ic_server,
    "graduationCap" to Res.drawable.ic_graduation_cap,
    "fileText" to Res.drawable.ic_file_text,
    "penLine" to Res.drawable.ic_pen_line,
    "smile" to Res.drawable.ic_smile,
    "coffee" to Res.drawable.ic_coffee,
    "repeat" to Res.drawable.ic_repeat,
    "hash" to Res.drawable.ic_hash,
    "messageCircle" to Res.drawable.ic_message_circle,
    "thumbsUp" to Res.drawable.ic_thumbs_up,
    "trendingUp" to Res.drawable.ic_trending_up,
    "trendingDown" to Res.drawable.ic_trending_down,
    "arrowUpRight" to Res.drawable.ic_arrow_up_right,
    "arrowRight" to Res.drawable.ic_arrow_right,
    "sliders" to Res.drawable.ic_sliders,
    "circle" to Res.drawable.ic_circle,
    "checkCircle" to Res.drawable.ic_check_circle,
    "check" to Res.drawable.ic_check,
    "x" to Res.drawable.ic_x,
    "plus" to Res.drawable.ic_plus,
    "chevronRight" to Res.drawable.ic_chevron_right,
    "chevronLeft" to Res.drawable.ic_chevron_left,
    "chevronDown" to Res.drawable.ic_chevron_down,
    "chevronUp" to Res.drawable.ic_chevron_up,
    "bell" to Res.drawable.ic_bell,
    "flame" to Res.drawable.ic_flame,
    "settings" to Res.drawable.ic_settings,
    "clock" to Res.drawable.ic_clock,
    "star" to Res.drawable.ic_star,
    "user" to Res.drawable.ic_user,
    "play" to Res.drawable.ic_play,
    "pause" to Res.drawable.ic_pause,
    "mic" to Res.drawable.ic_mic,
    "send" to Res.drawable.ic_send,
    "search" to Res.drawable.ic_search,
    "list" to Res.drawable.ic_list,
    "lock" to Res.drawable.ic_lock,
    "more" to Res.drawable.ic_more,
    "download" to Res.drawable.ic_download,
    "bookmark" to Res.drawable.ic_bookmark,
    "calendar" to Res.drawable.ic_calendar,
    "zap" to Res.drawable.ic_zap,
    "dumbbell" to Res.drawable.ic_dumbbell,
    "bedDouble" to Res.drawable.ic_bed_double,
)

/** TIcon — stroke icon tinted to [color]. Size in dp. */
@Composable
fun TfIcon(name: String, size: Int = 20, color: Color = Color.Unspecified, modifier: Modifier = Modifier) {
    val res = iconMap[name]
    if (res == null) {
        Box(modifier.size(size.dp))
        return
    }
    val tint = if (color == Color.Unspecified) LocalTf.current.fg else color
    Image(
        painter = painterResource(res),
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier.size(size.dp),
    )
}
