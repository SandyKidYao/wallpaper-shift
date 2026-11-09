package com.wallpapershift.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SetPackageTest {
    @get:Rule val temp = TemporaryFolder()

    private fun zip(vararg entries: Pair<String, String>, charset: Charset = Charsets.UTF_8): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes, charset).use { out ->
            for ((name, content) in entries) {
                out.putNextEntry(ZipEntry(name))
                out.write(content.toByteArray())
                out.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    private fun read(bytes: ByteArray, fallbackName: String = "菲伦") =
        SetPackage.read({ bytes.inputStream() }, fallbackName, temp.newFolder())

    private fun SetPackageContent.labels() = items.map { it.label }
    private fun SetPackageContent.contents() = items.map { it.file.readText() }

    @Test fun `pictures alone import in natural file-name order with defaults`() {
        val content = read(zip(
            "10.jpg" to "ten", "2.png" to "two", "1.JPG" to "one", "notes.txt" to "x",
            "__MACOSX/._1.JPG" to "fork", ".hidden.jpg" to "hidden",
        ))
        assertEquals("菲伦", content.name)
        assertEquals(WallpaperSet.DEFAULT_BLUR, content.blur)
        assertEquals(listOf("1", "2", "10"), content.labels())
        assertEquals(listOf("one", "two", "ten"), content.contents())
        assertTrue(content.items.all { it.startMinute == null })
    }

    @Test fun `manifest sets name, blur, order, times and labels`() {
        val manifest = """
            {"format":"wallpaper-shift.set","version":1,"name":"晨昏","blur":8,"future":"ignored",
             "items":[{"file":"b.jpg","start":"19:00"},{"file":"a.jpg","start":"5:00","label":"黎明"},{"file":"c.jpg"}]}
        """
        val content = read(zip("manifest.json" to manifest, "a.jpg" to "A", "b.jpg" to "B", "c.jpg" to "C", "extra.jpg" to "E"))
        assertEquals("晨昏", content.name)
        assertEquals(8, content.blur)
        assertEquals(listOf("b", "黎明", "c"), content.labels())
        assertEquals(listOf(19 * 60, 5 * 60, null), content.items.map { it.startMinute })
        assertEquals(0, content.missing)
    }

    @Test fun `a folder zipped on macOS resolves paths against the manifest's folder`() {
        val manifest = """{"items":[{"file":"01-dawn.jpg","start":"05:00"},{"file":"gone.jpg"}]}"""
        val content = read(zip("菲伦/manifest.json" to manifest, "菲伦/01-dawn.jpg" to "dawn", "__MACOSX/菲伦/._01-dawn.jpg" to "fork"))
        assertEquals(listOf("01-dawn"), content.labels())
        assertEquals(listOf("dawn"), content.contents())
        assertEquals(1, content.missing)
    }

    @Test fun `blur is clamped and a blank name falls back to the file name`() {
        val content = read(zip("manifest.json" to """{"name":"  ","blur":40}""", "x.jpg" to "x"), fallbackName = "旅行")
        assertEquals("旅行", content.name)
        assertEquals(WallpaperSet.MAX_BLUR, content.blur)
    }

    @Test fun `more than the limit keeps the first pictures and reports the rest`() {
        val pictures = (1..30).map { "%02d.jpg".format(it) to "$it" }.toTypedArray()
        val content = read(zip(*pictures))
        assertEquals(SetPackage.MAX_IMAGES, content.items.size)
        assertEquals("01", content.labels().first())
        assertEquals(30 - SetPackage.MAX_IMAGES, content.overLimit)
    }

    @Test fun `names from a Chinese Windows zip (GBK) are read correctly`() {
        val content = read(zip("黎明.jpg" to "dawn", "夜晚.jpg" to "night", charset = Charset.forName("GBK")))
        assertEquals(setOf("黎明", "夜晚"), content.labels().toSet())
    }

    @Test fun `files that are not wallpaper sets are rejected with a message`() {
        fun message(bytes: ByteArray) = assertThrows(SetPackageException::class.java) { read(bytes) }.message
        assertEquals(SetPackage.NOT_A_SET, message("just some text".toByteArray()))
        assertEquals(SetPackage.NOT_A_SET, message(zip("readme.txt" to "no pictures")))
        assertEquals(SetPackage.NOT_A_SET, message(zip("manifest.json" to "{not json", "a.jpg" to "a")))
        assertEquals(SetPackage.NOT_A_SET, message(zip("manifest.json" to """{"format":"other.app"}""", "a.jpg" to "a")))
        assertEquals(SetPackage.NOT_A_SET, message(zip("manifest.json" to """{"items":[{"file":"gone.jpg"}]}""", "a.jpg" to "a")))
        assertEquals(SetPackage.NEWER_VERSION, message(zip("manifest.json" to """{"version":2}""", "a.jpg" to "a")))
    }

    @Test fun `an exported set imports back unchanged`() {
        val items = listOf(
            WallpaperItem("a", 5 * 60, "01-dawn"),
            WallpaperItem("b", 7 * 60 + 30, "早晨/晴"),
            WallpaperItem("c", 11 * 60, "01-dawn"),
            WallpaperItem("d", 19 * 60, "", ThemeAnchor.DARK_START),
        )
        val set = WallpaperSet(id = "s", name = "菲伦", blurRadius = 8, items = items)
        val pictures = temp.newFolder()
        items.forEach { java.io.File(pictures, it.id).writeText("pixels of ${it.id}") }
        val bytes = ByteArrayOutputStream().also { out -> SetPackage.write(out, set) { java.io.File(pictures, it.id) } }.toByteArray()

        val content = read(bytes, fallbackName = "ignored")
        assertEquals("菲伦", content.name)
        assertEquals(8, content.blur)
        assertEquals(items.map { it.startMinute }, content.items.map { it.startMinute })
        assertEquals(items.map { it.anchor }, content.items.map { it.anchor })
        assertEquals(listOf("01-dawn", "早晨/晴", "01-dawn", "图片 4"), content.labels())
        assertEquals(items.map { "pixels of ${it.id}" }, content.contents())
    }

    @Test fun `start times parse as 24-hour HH-mm only`() {
        assertEquals(5 * 60, SetPackage.parseStart("05:00"))
        assertEquals(5 * 60, SetPackage.parseStart(" 5:00 "))
        assertEquals(23 * 60 + 59, SetPackage.parseStart("23:59"))
        assertNull(SetPackage.parseStart("24:00"))
        assertNull(SetPackage.parseStart("7:5"))
        assertNull(SetPackage.parseStart("早上"))
    }

    @Test fun `missing start times fill the gaps around the given ones`() {
        assertEquals(listOf(6 * 60, 14 * 60, 22 * 60), SetPackage.planStarts(listOf(null, null, null)))
        val planned = SetPackage.planStarts(listOf(5 * 60, null, 19 * 60))
        assertEquals(5 * 60, planned[0])
        assertEquals(19 * 60, planned[2])
        assertEquals(12 * 60, planned[1]) // middle of the largest gap, 05:00–19:00
    }
}
