package com.wallpapershift.app.data

import com.wallpapershift.app.schedule.DaySchedule
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * A wallpaper set packed for import: an ordinary zip of pictures, optionally with a `manifest.json`
 * that names the set and gives each picture a start time (format in design/README.md §8.9).
 * Pure JVM, so it is unit-tested; the Android side only supplies the stream and decodes the pictures.
 */
object SetPackage {
    const val FORMAT = "wallpaper-shift.set"
    const val VERSION = 1
    const val MAX_IMAGES = 24
    private const val MANIFEST = "manifest.json"
    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "avif")
    // Guards against zip bombs: no real wallpaper comes near these.
    private const val MAX_IMAGE_BYTES = 64L * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 512L * 1024 * 1024
    private const val MAX_MANIFEST_BYTES = 256L * 1024

    /** Manifest spelling of the theme anchors; [ThemeAnchor.NONE] is written as no field at all. */
    private val ANCHOR_NAMES = mapOf(ThemeAnchor.DARK_START to "dark-start", ThemeAnchor.LIGHT_START to "light-start")

    private val json = Json { ignoreUnknownKeys = true }
    private val prettyJson = Json { prettyPrint = true; encodeDefaults = true; explicitNulls = false }

    /**
     * Unpacks the pictures from the zip that [open] streams into [workDir] and works out what to import.
     * [fallbackName] (usually the zip's file name) names the set when the manifest doesn't.
     * [open] may be called twice: names that aren't UTF-8 (zips made on Chinese Windows) are retried as GBK.
     *
     * @throws SetPackageException with a message for the user.
     */
    fun read(open: () -> InputStream, fallbackName: String, workDir: File): SetPackageContent {
        val entries = try {
            unzip(open, Charsets.UTF_8, workDir)
        } catch (_: IllegalArgumentException) {
            workDir.deleteRecursively()
            unzip(open, Charset.forName("GBK"), workDir)
        }
        if (entries.images.isEmpty()) throw SetPackageException(NOT_A_SET)
        val manifest = entries.manifest?.let { (_, text) ->
            runCatching { json.decodeFromString(SetManifest.serializer(), text) }.getOrElse { throw SetPackageException(NOT_A_SET) }
        }
        if (manifest?.format != null && manifest.format != FORMAT) throw SetPackageException(NOT_A_SET)
        if (manifest != null && manifest.version > VERSION) throw SetPackageException(NEWER_VERSION)

        val base = entries.manifest?.first?.substringBeforeLast('/', "")?.let { if (it.isEmpty()) it else "$it/" }.orEmpty()
        var missing = 0
        val listed = manifest?.items
        val items = if (listed == null) {
            entries.images.keys.filter { it.startsWith(base) }.sortedWith(NaturalOrder)
                .map { path -> PackageItem(entries.images.getValue(path), stem(path), startMinute = null) }
        } else {
            listed.mapNotNull { item ->
                val file = resolve(item.file, base, entries.images)
                if (file == null) {
                    missing++
                    return@mapNotNull null
                }
                PackageItem(
                    file,
                    item.label?.trim()?.takeIf { it.isNotEmpty() } ?: stem(item.file),
                    item.start?.let(::parseStart),
                    ANCHOR_NAMES.entries.firstOrNull { it.value == item.anchor?.trim() }?.key ?: ThemeAnchor.NONE,
                )
            }
        }
        if (items.isEmpty()) throw SetPackageException(NOT_A_SET)
        return SetPackageContent(
            name = manifest?.name?.trim()?.takeIf { it.isNotEmpty() } ?: fallbackName.trim().ifEmpty { "导入的壁纸集" },
            blur = (manifest?.blur ?: WallpaperSet.DEFAULT_BLUR).coerceIn(0, WallpaperSet.MAX_BLUR),
            items = items.take(MAX_IMAGES),
            overLimit = (items.size - MAX_IMAGES).coerceAtLeast(0),
            missing = missing,
        )
    }

    /**
     * Packs [set] as a zip on [out] (closing it): a manifest with the name, blur and each picture's start
     * time and label, then the pictures, from [imageOf]. Reading it back gives the same set.
     */
    fun write(out: OutputStream, set: WallpaperSet, imageOf: (WallpaperItem) -> File) {
        val taken = mutableSetOf<String>()
        val files = set.items.mapIndexed { index, item ->
            val base = safeFileName(item.label).ifEmpty { "图片 ${index + 1}" }
            var name = "$base.jpg"
            var n = 2
            while (!taken.add(name.lowercase())) name = "$base-${n++}.jpg"
            name
        }
        val manifest = SetManifest(
            format = FORMAT,
            version = VERSION,
            name = set.name,
            blur = set.blurRadius,
            items = set.items.zip(files) { item, file ->
                ManifestItem(file, DaySchedule.format(item.startMinute), item.label, ANCHOR_NAMES[item.anchor])
            },
        )
        ZipOutputStream(out.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(prettyJson.encodeToString(SetManifest.serializer(), manifest).toByteArray())
            zip.closeEntry()
            set.items.zip(files).forEach { (item, file) ->
                zip.putNextEntry(ZipEntry(file))
                imageOf(item).inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /** [text] usable as a file name on any system: no path separators or reserved characters. */
    fun safeFileName(text: String): String = text.replace(Regex("""[\\/:*?"<>|\p{Cntrl}]"""), "_").trim().trimStart('.')

    /**
     * Start minutes for every item: the manifest's times where given, the rest spread over the free
     * time the same way as adding pictures to a set ([DaySchedule.suggestStarts]).
     */
    fun planStarts(starts: List<Int?>): List<Int> {
        val auto = DaySchedule.suggestStarts(starts.filterNotNull().distinct(), starts.count { it == null }).iterator()
        return starts.map { it ?: auto.next() }
    }

    /** "05:00" or "5:00" → minute of day; anything else (including "24:00") → null. */
    fun parseStart(text: String): Int? {
        val match = Regex("""(\d{1,2}):(\d{2})""").matchEntire(text.trim()) ?: return null
        val (h, m) = match.destructured.toList().map(String::toInt)
        return if (h in 0..23 && m in 0..59) h * 60 + m else null
    }

    private class Entries(val images: Map<String, File>, val manifest: Pair<String, String>?)

    private fun unzip(open: () -> InputStream, charset: Charset, workDir: File): Entries {
        workDir.mkdirs()
        val images = linkedMapOf<String, File>()
        var manifest: Pair<String, String>? = null
        var total = 0L
        try {
            ZipInputStream(open().buffered(), charset).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val path = entry.name.replace('\\', '/').trimStart('/')
                    val segments = path.split('/')
                    // Skip folders, macOS resource forks (__MACOSX/, ._name) and other hidden files.
                    if (entry.isDirectory || segments.any { it.startsWith('.') || it == "__MACOSX" }) continue
                    val name = segments.last()
                    when {
                        name.equals(MANIFEST, ignoreCase = true) -> {
                            // The shallowest manifest wins: it describes the package, not a nested folder.
                            val current = manifest
                            if (current == null || depth(path) < depth(current.first)) {
                                manifest = path to String(zip.readCapped(MAX_MANIFEST_BYTES), Charsets.UTF_8)
                            }
                        }
                        name.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS -> {
                            val file = File(workDir, "${images.size}.${name.substringAfterLast('.').lowercase()}")
                            file.outputStream().use { out -> total += zip.copyCapped(out, MAX_IMAGE_BYTES) }
                            if (total > MAX_TOTAL_BYTES) throw SetPackageException(NOT_A_SET)
                            images[path] = file
                        }
                    }
                }
            }
        } catch (_: ZipException) {
            throw SetPackageException(NOT_A_SET)
        } catch (error: IOException) {
            if (error is SetPackageException) throw error
            throw SetPackageException(UNREADABLE)
        }
        return Entries(images, manifest)
    }

    /** A manifest path, relative to the manifest's folder; falls back to a unique file-name match. */
    private fun resolve(file: String, base: String, images: Map<String, File>): File? {
        val path = file.replace('\\', '/').removePrefix("./").trimStart('/')
        images[base + path]?.let { return it }
        val byName = images.filterKeys { it.substringAfterLast('/') == path.substringAfterLast('/') }.values
        return byName.singleOrNull()
    }

    private fun stem(path: String) = path.substringAfterLast('/').substringBeforeLast('.')

    private fun depth(path: String) = path.count { it == '/' }

    private fun InputStream.readCapped(limit: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        copyCapped(out, limit)
        return out.toByteArray()
    }

    private fun InputStream.copyCapped(out: java.io.OutputStream, limit: Long): Long {
        val buffer = ByteArray(64 * 1024)
        var copied = 0L
        while (true) {
            val n = read(buffer)
            if (n < 0) return copied
            copied += n
            if (copied > limit) throw SetPackageException(NOT_A_SET)
            out.write(buffer, 0, n)
        }
    }

    /** "2.jpg" before "10.jpg": runs of digits compare by value. */
    private object NaturalOrder : Comparator<String> {
        private val chunks = Regex("""\d+|\D+""")
        override fun compare(a: String, b: String): Int {
            val x = chunks.findAll(a.lowercase()).map { it.value }.toList()
            val y = chunks.findAll(b.lowercase()).map { it.value }.toList()
            for (i in 0 until minOf(x.size, y.size)) {
                val (p, q) = x[i] to y[i]
                val c = if (p[0].isDigit() && q[0].isDigit()) p.toBigInteger().compareTo(q.toBigInteger()) else p.compareTo(q)
                if (c != 0) return c
            }
            return x.size - y.size
        }
    }

    const val NOT_A_SET = "这个文件不是壁纸集"
    const val NEWER_VERSION = "这个壁纸集需要更新版本的壁纸时移"
    const val UNREADABLE = "读取文件失败"
}

/** A user-facing reason the package can't be imported. */
class SetPackageException(message: String) : IOException(message)

@Serializable
data class SetManifest(
    val format: String? = null,
    val version: Int = SetPackage.VERSION,
    val name: String? = null,
    val blur: Int? = null,
    /** Null: import every picture in the package, sorted by file name. */
    val items: List<ManifestItem>? = null,
)

@Serializable
data class ManifestItem(
    /** Path inside the zip, relative to the manifest's folder. */
    val file: String,
    /** "HH:mm"; missing or invalid → spread automatically. */
    val start: String? = null,
    /** Defaults to the file name without extension. */
    val label: String? = null,
    /** "dark-start" or "light-start": start when the phone's theme flips; [start] is the fallback. Unknown values are ignored. */
    val anchor: String? = null,
)

data class PackageItem(val file: File, val label: String, val startMinute: Int?, val anchor: ThemeAnchor = ThemeAnchor.NONE)

data class SetPackageContent(
    val name: String,
    val blur: Int,
    val items: List<PackageItem>,
    /** Pictures beyond [SetPackage.MAX_IMAGES], left out. */
    val overLimit: Int,
    /** Manifest entries whose file isn't in the zip. */
    val missing: Int,
)
