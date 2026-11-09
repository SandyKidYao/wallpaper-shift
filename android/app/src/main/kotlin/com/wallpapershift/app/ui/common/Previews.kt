package com.wallpapershift.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wallpapershift.app.data.WallpaperItem
import com.wallpapershift.app.schedule.DaySchedule
import java.io.File

enum class ScreenKind { Lock, Home }

/** A miniature phone showing [file] as it would look on the lock or home screen. */
@Composable
fun PhonePreview(file: File?, kind: ScreenKind, blurRadius: Int, modifier: Modifier = Modifier, clock: String = "") {
    val shape = RoundedCornerShape(14.dp)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 19.5f)
                .clip(shape)
                .border(2.dp, MaterialTheme.colorScheme.outlineVariant, shape),
        ) {
            if (file != null) {
                FileImage(file, Modifier.fillMaxSize(), blurRadius = if (kind == ScreenKind.Home) blurRadius else 0)
            } else {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
            }
            when (kind) {
                ScreenKind.Lock -> Text(
                    clock,
                    Modifier.align(Alignment.TopCenter).padding(top = 22.dp),
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Light,
                )
                ScreenKind.Home -> AppIconGrid(Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp))
            }
        }
        Text(
            if (kind == ScreenKind.Lock) "锁屏" else "桌面",
            Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AppIconGrid(modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(4) { Box(Modifier.size(14.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.75f))) }
            }
        }
    }
}

private val ThumbGap = 6.dp
/** Below this width each picture is too small to read, so the row scrolls instead of shrinking. */
private val MinFitWidth = 40.dp
private val ScrollThumbWidth = 60.dp
private const val SCREEN_ASPECT = 9f / 19.5f

/**
 * The day's pictures in time order, each shown whole in a phone-shaped frame with its start time
 * (it lasts until the next one starts). They share the width while they fit at a readable size;
 * with more pictures the row scrolls sideways and opens on the current one. The picture showing now
 * is outlined. [onItemClick] makes the pictures tappable; without it taps reach the parent.
 */
@Composable
fun DayTimeline(
    items: List<WallpaperItem>,
    thumbOf: (String) -> File,
    nowMinute: Int?,
    modifier: Modifier = Modifier,
    onItemClick: ((WallpaperItem) -> Unit)? = null,
) {
    if (items.isEmpty()) return
    val currentId = nowMinute?.let { DaySchedule.activeItem(items, it)?.id }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val fitWidth = (maxWidth - ThumbGap * (items.size - 1)) / items.size
        if (fitWidth >= MinFitWidth) {
            Row(horizontalArrangement = Arrangement.spacedBy(ThumbGap)) {
                items.forEach { item ->
                    DayThumb(item, thumbOf(item.id), item.id == currentId, Modifier.weight(1f), onItemClick)
                }
            }
        } else {
            val listState = rememberLazyListState()
            val currentIndex = items.indexOfFirst { it.id == currentId }
            // Open on the current picture with one of context to its left, else at the start. Re-run when
            // pictures are added: a keyed list otherwise stays anchored to whichever picture was first,
            // pushing earlier-starting imports out of view.
            LaunchedEffect(currentIndex, items.size) { listState.scrollToItem((currentIndex - 1).coerceAtLeast(0)) }
            LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(ThumbGap)) {
                items(items, key = { it.id }) { item ->
                    DayThumb(item, thumbOf(item.id), item.id == currentId, Modifier.width(ScrollThumbWidth), onItemClick)
                }
            }
        }
    }
}

@Composable
private fun DayThumb(item: WallpaperItem, thumb: File, isNow: Boolean, modifier: Modifier, onClick: ((WallpaperItem) -> Unit)?) {
    val shape = RoundedCornerShape(8.dp)
    val accent = MaterialTheme.colorScheme.primary
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        FileImage(
            thumb,
            Modifier
                .fillMaxWidth()
                .aspectRatio(SCREEN_ASPECT)
                .clip(shape)
                .then(if (isNow) Modifier.border(2.5.dp, accent, shape) else Modifier)
                .then(if (onClick != null) Modifier.clickable { onClick(item) } else Modifier),
            contentDescription = item.label.ifEmpty { null },
            contentScale = ContentScale.Fit,
        )
        Text(
            DaySchedule.format(item.startMinute),
            Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isNow) FontWeight.Bold else FontWeight.Normal,
            color = if (isNow) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
