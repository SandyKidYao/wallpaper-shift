package com.wallpapershift.app.ui.editor

import android.content.ClipData
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wallpapershift.app.MainActivity
import com.wallpapershift.app.R
import com.wallpapershift.app.data.SetExporter
import com.wallpapershift.app.data.ThemeAnchor
import com.wallpapershift.app.data.WallpaperItem
import com.wallpapershift.app.data.WallpaperSet
import com.wallpapershift.app.schedule.AnchorTiming
import com.wallpapershift.app.schedule.DaySchedule
import com.wallpapershift.app.schedule.ThemeTimes
import com.wallpapershift.app.ui.common.DayTimeline
import com.wallpapershift.app.ui.common.FileImage
import com.wallpapershift.app.ui.common.PhonePreview
import com.wallpapershift.app.ui.common.ScreenKind
import java.io.File
import java.time.LocalTime

private const val MAX_PICK = 24

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(viewModel: EditorViewModel, thumbOf: (String) -> File, onBack: () -> Unit) {
    val set by viewModel.set.collectAsStateWithLifecycle()
    val isActive by viewModel.isActive.collectAsStateWithLifecycle()
    val importing by viewModel.importing.collectAsStateWithLifecycle()
    val exporting by viewModel.exporting.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf(false) }
    var editingTimeOf by remember { mutableStateOf<WallpaperItem?>(null) }
    var previewItemId by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PICK)) {
        viewModel.addImages(it)
    }

    // Android 9 can't add downloads without a permission, so the user picks where to save instead.
    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(SetExporter.MIME_TYPE)) { uri ->
        uri?.let(viewModel::exportTo)
    }

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(Unit) {
        viewModel.exported.collect { saved ->
            if (saved == null) {
                snackbar.showSnackbar("已导出")
            } else if (snackbar.showSnackbar("已导出到「${SetExporter.PLACE}」", actionLabel = "分享") == SnackbarResult.ActionPerformed) {
                val send = Intent(Intent.ACTION_SEND)
                    .setType(SetExporter.MIME_TYPE)
                    .putExtra(Intent.EXTRA_STREAM, saved.uri)
                    .putExtra(Intent.EXTRA_TITLE, saved.name)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                // With the name as the clip's label, share sheets show 「菲伦.zip」 rather than the media id.
                send.clipData = ClipData(saved.name, arrayOf(SetExporter.MIME_TYPE), ClipData.Item(saved.uri))
                // We accept shared zips ourselves; offering to import our own export back would only confuse.
                val chooser = Intent.createChooser(send, null)
                    .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(context, MainActivity::class.java)))
                context.startActivity(chooser)
            }
        }
    }

    val current = set ?: return // Deleted: the caller navigates away.

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("编辑壁纸集") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), "返回") } },
                actions = {
                    TextButton(
                        onClick = { if (Build.VERSION.SDK_INT >= 29) viewModel.export() else saveAs.launch(SetExporter.fileName(current)) },
                        enabled = current.items.isNotEmpty() && !exporting,
                    ) { Text("导出") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(painterResource(R.drawable.ic_delete), "删除壁纸集") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val nowMinute = LocalTime.now().let { it.hour * 60 + it.minute }
        // Theme-anchored items sit at the system's switch time, so the list and timeline show real starts.
        val theme = viewModel.themeTimes()
        val shownItems = DaySchedule.resolve(current.items, theme)
        val previewItem = shownItems.firstOrNull { it.id == previewItemId } ?: DaySchedule.activeItem(shownItems, nowMinute)
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "name") { NameField(current.name, viewModel::rename) }
            item(key = "active") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("启用此壁纸集", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    Switch(checked = isActive, onCheckedChange = viewModel::setActive)
                }
            }
            if (previewItem != null) {
                item(key = "blur") { BlurCard(current, previewItem, thumbOf, onBlurChosen = viewModel::setBlur) }
            }
            item(key = "schedule-title") {
                Column {
                    Text("时间表", style = MaterialTheme.typography.titleMedium)
                    if (current.items.isNotEmpty()) {
                        DayTimeline(
                            shownItems, thumbOf, nowMinute.takeIf { isActive }, Modifier.padding(top = 8.dp),
                            onItemClick = { previewItemId = it.id },
                        )
                    }
                }
            }
            items(shownItems, key = { it.id }) { item ->
                ItemRow(
                    item = item,
                    endMinute = DaySchedule.endMinute(shownItems, shownItems.indexOf(item)),
                    anchorNote = anchorNote(item, theme),
                    onSetAnchor = { viewModel.setAnchor(item.id, it) },
                    thumb = thumbOf(item.id),
                    selected = item.id == previewItem?.id,
                    onSelect = { previewItemId = item.id },
                    onEditTime = { editingTimeOf = item },
                    onApplyNow = { viewModel.applyNow(item.id) },
                    onDelete = { viewModel.deleteItem(item.id) },
                )
            }
            item(key = "add") {
                val progress = importing
                if (progress != null) {
                    Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("正在导入 ${progress.done}/${progress.total}…")
                    }
                } else {
                    Button(
                        onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        Modifier.fillMaxWidth(),
                    ) {
                        Icon(painterResource(R.drawable.ic_add_photo), null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (current.items.isEmpty()) "添加图片" else "继续添加图片")
                    }
                }
            }
        }
    }

    editingTimeOf?.let { item ->
        StartTimeDialog(
            initialMinute = item.startMinute,
            onDismiss = { editingTimeOf = null },
            onConfirm = { minute ->
                editingTimeOf = null
                viewModel.setStart(item.id, minute)
            },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除「${current.name}」？") },
            text = { Text("壁纸集和其中导入的图片副本都会被删除。当前桌面和锁屏不会改变。") },
            confirmButton = {
                // Filled red: the dark theme's error tone alone is too close to the accent to stand out.
                Button(
                    onClick = {
                        confirmDelete = false
                        viewModel.deleteSet(onBack)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun NameField(name: String, onChange: (String) -> Unit) {
    // Local state keeps the cursor stable; every edit is persisted straight away.
    var text by rememberSaveable { mutableStateOf(name) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onChange(it)
        },
        label = { Text("名称") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun BlurCard(set: WallpaperSet, item: WallpaperItem, thumbOf: (String) -> File, onBlurChosen: (Int) -> Unit) {
    var radius by remember(set.blurRadius) { mutableIntStateOf(set.blurRadius) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(horizontal = 24.dp)) {
                PhonePreview(thumbOf(item.id), ScreenKind.Lock, radius, Modifier.weight(1f), clock = DaySchedule.format(item.startMinute))
                PhonePreview(thumbOf(item.id), ScreenKind.Home, radius, Modifier.weight(1f))
            }
            Text(
                "桌面模糊：${if (radius == 0) "关" else radius}",
                Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.titleSmall,
            )
            Slider(
                value = radius.toFloat(),
                onValueChange = { radius = it.toInt() },
                onValueChangeFinished = { onBlurChosen(radius) },
                valueRange = 0f..WallpaperSet.MAX_BLUR.toFloat(),
                steps = WallpaperSet.MAX_BLUR - 1,
            )
        }
    }
}

@Composable
private fun ItemRow(
    item: WallpaperItem,
    endMinute: Int,
    anchorNote: String?,
    onSetAnchor: (ThemeAnchor) -> Unit,
    thumb: File,
    selected: Boolean,
    onSelect: () -> Unit,
    onEditTime: () -> Unit,
    onApplyNow: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Card(
        onClick = onSelect,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            FileImage(thumb, Modifier.size(width = 44.dp, height = 88.dp).clip(RoundedCornerShape(8.dp)))
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(
                    DaySchedule.format(item.startMinute),
                    Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onEditTime),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "显示到 ${if (endMinute <= item.startMinute) "次日 " else ""}${DaySchedule.format(endMinute)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (anchorNote != null) {
                    Text(anchorNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                }
                if (item.label.isNotEmpty()) {
                    Text(item.label, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                }
            }
            IconButton(onClick = onEditTime) { Icon(painterResource(R.drawable.ic_schedule), "修改开始时间") }
            // The menu positions itself against its parent, so it shares a Box with the button it belongs to.
            Box {
                IconButton(onClick = { menu = true }) { Icon(painterResource(R.drawable.ic_more_vert), "更多") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("立即应用这张") }, onClick = {
                        menu = false
                        onApplyNow()
                    })
                    if (item.anchor != ThemeAnchor.DARK_START) {
                        DropdownMenuItem(text = { Text("在日落时开始") }, onClick = {
                            menu = false
                            onSetAnchor(ThemeAnchor.DARK_START)
                        })
                    }
                    if (item.anchor != ThemeAnchor.LIGHT_START) {
                        DropdownMenuItem(text = { Text("在日出时开始") }, onClick = {
                            menu = false
                            onSetAnchor(ThemeAnchor.LIGHT_START)
                        })
                    }
                    if (item.anchor != ThemeAnchor.NONE) {
                        DropdownMenuItem(text = { Text("改回固定时间") }, onClick = {
                            menu = false
                            onSetAnchor(ThemeAnchor.NONE)
                        })
                    }
                    DropdownMenuItem(text = { Text("从壁纸集移除") }, onClick = {
                        menu = false
                        onDelete()
                    })
                }
            }
        }
    }
}

/** The row already carries the resolved start; explain the source, bounds and fallback separately. */
private fun anchorNote(item: WallpaperItem, theme: ThemeTimes): String? {
    val name = when (item.anchor) {
        ThemeAnchor.NONE -> return null
        ThemeAnchor.DARK_START -> "日落"
        ThemeAnchor.LIGHT_START -> "日出"
    }
    val range = AnchorTiming.safeRange(item.anchor) ?: return null
    val source = AnchorTiming.sourceMinute(item.anchor, theme)
    val used = DaySchedule.format(item.startMinute)
    val bounds = "最早 ${DaySchedule.format(range.first)}，最晚 ${DaySchedule.format(range.last)}"
    return when {
        source == null -> "跟随$name · $bounds\n读不到${name}时间，暂按 $used 切换"
        source < range.first -> "跟随$name · $bounds\n$name ${DaySchedule.format(source)}，按安全下限 $used"
        source > range.last -> "跟随$name · $bounds\n$name ${DaySchedule.format(source)}，按安全上限 $used"
        else -> "跟随$name · $bounds\n按 $used 切换"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartTimeDialog(initialMinute: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = initialMinute / 60, initialMinute = initialMinute % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("开始时间") },
        text = { TimePicker(state) },
        confirmButton = { TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
