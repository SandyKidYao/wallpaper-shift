package com.wallpapershift.app.ui.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wallpapershift.app.R
import com.wallpapershift.app.data.WallpaperSet
import com.wallpapershift.app.schedule.BackgroundSettings
import com.wallpapershift.app.schedule.DaySchedule
import com.wallpapershift.app.schedule.Reliability
import com.wallpapershift.app.ui.common.DayTimeline
import com.wallpapershift.app.ui.common.PhonePreview
import com.wallpapershift.app.ui.common.ScreenKind
import java.io.File
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    thumbOf: (String) -> File,
    onOpenSet: (String) -> Unit,
    /** A set package another app asked us to open, imported once and then reported handled. */
    incomingPackage: Uri? = null,
    onIncomingHandled: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val reliability by viewModel.reliability.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val pickPackage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importPackage)
    }

    LifecycleResumeEffect(Unit) {
        viewModel.refreshReliability()
        onPauseOrDispose {}
    }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(incomingPackage) {
        incomingPackage?.let {
            viewModel.importPackage(it)
            onIncomingHandled()
        }
    }
    val showReliability = state.activeSet != null && reliability.atRisk(state.missedAt)
    // Where 「我的壁纸集」 sits in the list below. A just-imported set is listed first, right under it.
    val setsTitleIndex by rememberUpdatedState(listOf(state.sets.isNotEmpty(), showReliability, busy != null).count { it })
    LaunchedEffect(Unit) {
        viewModel.imported.collect {
            withFrameNanos {}
            listState.animateScrollToItem(setsTitleIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("壁纸时移") },
                actions = {
                    TextButton(onClick = { pickPackage.launch(ZIP_TYPES) }, enabled = busy == null) { Text("导入") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.createSet(onOpenSet) },
                icon = { Icon(painterResource(R.drawable.ic_add), null) },
                text = { Text("新建壁纸集") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // With no sets at all, the empty state below says everything; the card would only repeat it.
            if (state.sets.isNotEmpty()) item { NowCard(state, thumbOf, onRetry = viewModel::retry, onResume = viewModel::resumeSchedule) }
            if (showReliability) {
                item {
                    ReliabilityCard(
                        reliability = reliability,
                        missedAt = state.missedAt,
                        onOpenStep = { step -> viewModel.settingsIntent(step)?.let { context.launch(it) } },
                        onConfirmStep = viewModel::confirmStep,
                        context = context,
                    )
                }
            }
            busy?.let { label ->
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(label)
                    }
                }
            }
            item {
                Text("我的壁纸集", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleMedium)
            }
            if (state.sets.isEmpty()) {
                if (busy == null) item { EmptyHint() }
            }
            items(state.sets, key = { it.id }) { set ->
                val active = set.id == state.activeSet?.id
                SetCard(
                    set = set,
                    active = active,
                    nowMinute = state.nowMinute.takeIf { active },
                    thumbOf = thumbOf,
                    onOpen = { onOpenSet(set.id) },
                    onActiveChange = { viewModel.setActive(set.id, it) },
                )
            }
        }
    }
}

/** What the file chooser offers for 「导入」: set packages are plain zip files. */
private val ZIP_TYPES = arrayOf("application/zip", "application/x-zip-compressed")

@Composable
private fun NowCard(state: HomeUiState, thumbOf: (String) -> File, onRetry: () -> Unit, onResume: () -> Unit) {
    val set = state.activeSet
    val item = state.currentItem
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp)) {
            if (set == null || item == null) {
                Text("未启用", style = MaterialTheme.typography.titleMedium)
                Text("打开下方壁纸集的开关，开始按时间切换。", Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodyMedium)
                return@Column
            }
            // Show what is really on the phone: during a preview that's the previewed picture.
            val shown = state.previewItem ?: item
            val blur = if (state.previewItem != null) state.previewBlur else set.blurRadius
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                val clock = state.now.format(DateTimeFormatter.ofPattern("HH:mm"))
                PhonePreview(thumbOf(shown.id), ScreenKind.Lock, blur, Modifier.weight(1f), clock = clock)
                PhonePreview(thumbOf(shown.id), ScreenKind.Home, blur, Modifier.weight(1f))
            }
            val index = set.items.indexOf(item)
            Text(
                shown.label.ifEmpty { "第 ${index + 1} 张" },
                Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.titleMedium,
            )
            val next = state.nextSwitchAt?.format(DateTimeFormatter.ofPattern("HH:mm"))
            val range = "${DaySchedule.format(item.startMinute)}–${DaySchedule.format(DaySchedule.endMinute(set.items, index))}"
            Text(
                when {
                    state.previewItem != null -> if (next == null) "预览中" else "预览中 · $next 自动恢复按时间表"
                    next == null -> "全天显示这一张"
                    else -> "$range · $next 换成 ${state.nextItem?.label.orEmpty()}"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            when {
                state.lastError != null -> CardAction("上次设置壁纸失败：${state.lastError}", "重试", onRetry, error = true)
                state.previewing -> CardAction(null, "恢复按时间表", onResume)
            }
        }
    }
}

/** The card's one action when something needs doing. A filled button: a tonal one vanishes on this card. */
@Composable
private fun CardAction(text: String?, action: String, onClick: () -> Unit, error: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (text != null) {
            Text(
                text,
                Modifier.weight(1f).padding(end = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Button(onClick = onClick) { Text(action) }
    }
}

/**
 * The generic battery whitelist is only offered on phones without known OEM steps: on vivo it neither
 * stops the freezing nor is needed once 「后台耗电」 is allowed.
 */
private fun Reliability.showBatteryRow() = !batteryUnrestricted && oemSteps.isEmpty()

private fun Reliability.atRisk(missedAt: ZonedDateTime?) =
    missedAt != null || !exactAlarms || showBatteryRow() || oemUnconfirmed

@Composable
private fun ReliabilityCard(
    reliability: Reliability,
    missedAt: ZonedDateTime?,
    onOpenStep: (BackgroundSettings.Step) -> Unit,
    onConfirmStep: (BackgroundSettings.Step) -> Unit,
    context: Context,
) {
    // After a missed switch every step shows again, so the user can check each one.
    val steps = reliability.oemSteps.filter { (_, status) -> !status.done || missedAt != null }
    val count = steps.size + (if (reliability.exactAlarms) 0 else 1) + (if (reliability.showBatteryRow()) 1 else 0)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_warning), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        if (missedAt != null) "${missedAt.format(DateTimeFormatter.ofPattern("HH:mm"))} 的切换没有执行" else "还需打开 $count 项设置才能准时切换",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    if (missedAt != null && count > 0) {
                        Text("请检查下面的手机设置", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
            steps.forEach { (step, status) ->
                // A detected step goes away by itself once it's on; only an undetected one needs the user's word.
                val onDone = if (status.detected) null else ({ onConfirmStep(step) })
                when (step) {
                    BackgroundSettings.Step.Autostart ->
                        SettingRow("自启动", null, "去开启", onClick = { onOpenStep(step) }, onDone = onDone)
                    BackgroundSettings.Step.BackgroundPower ->
                        SettingRow("后台耗电", "电量 → 后台耗电管理 → 允许后台耗电", "去设置", onClick = { onOpenStep(step) }, onDone = onDone)
                    BackgroundSettings.Step.SleepStandby ->
                        SettingRow("睡眠待机优化", "电池 → 省电管理 → 关闭睡眠待机优化", "去关闭", onClick = { onOpenStep(step) }, onDone = onDone)
                }
            }
            if (!reliability.exactAlarms) {
                SettingRow("闹钟和提醒", null, "去开启", onClick = {
                    if (Build.VERSION.SDK_INT >= 31) {
                        context.launch(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri()))
                    }
                })
            }
            if (reliability.showBatteryRow()) {
                SettingRow("电池优化", "设为不限制", "去设置", onClick = {
                    context.launch(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${context.packageName}".toUri()),
                        fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                    )
                })
            }
        }
    }
}

/** One setting the user has to change: its name, an optional path to it, and the buttons. */
@Composable
private fun SettingRow(name: String, path: String?, action: String, onClick: () -> Unit, onDone: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(start = 28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onErrorContainer)
            // Outlined, not plain text: as bare text 「已设置」 read like a status, and users never tapped it.
            if (onDone != null) {
                OutlinedButton(
                    onClick = onDone,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer),
                ) { Text("我已设置") }
                Spacer(Modifier.width(8.dp))
            }
            FilledTonalButton(onClick = onClick) { Text(action) }
        }
        // On its own line: squeezed beside the buttons it wrapped mid-word.
        if (path != null) {
            Text(path, Modifier.padding(bottom = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

private fun Context.launch(intent: Intent, fallback: Intent? = null) {
    try {
        startActivity(intent)
    } catch (_: RuntimeException) {
        // ActivityNotFoundException, or SecurityException from an OEM page that changed.
        fallback?.let { runCatching { startActivity(it) } }
    }
}

@Composable
private fun SetCard(
    set: WallpaperSet,
    active: Boolean,
    nowMinute: Int?,
    thumbOf: (String) -> File,
    onOpen: () -> Unit,
    onActiveChange: (Boolean) -> Unit,
) {
    Card(onClick = onOpen) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(set.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (set.items.isEmpty()) "还没有图片" else "${set.items.size} 张 · 桌面模糊 ${set.blurRadius}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = active, onCheckedChange = onActiveChange)
            }
            if (set.items.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                DayTimeline(set.items, thumbOf, nowMinute)
            }
        }
    }
}

@Composable
private fun EmptyHint() {
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(painterResource(R.drawable.ic_wallpaper), null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
        Text("还没有壁纸集", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyLarge)
        Text("点右下角「新建壁纸集」开始", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
