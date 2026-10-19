package com.safemode.hbconnect.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageMimeTest {
    @Test
    fun mimeFromExtension() {
        assertEquals("image/jpeg", imageMimeType("photo_1.jpg"))
        assertEquals("image/png", imageMimeType("op_0.PNG"))
        assertEquals("image/webp", imageMimeType("a.b.webp"))
        assertEquals("image/jpeg", imageMimeType("no_extension"))
    }

    @Test
    fun extensionRoundTrips() {
        listOf("image/png", "image/webp", "image/gif", "image/heic", "image/heif", "image/bmp", "image/jpeg")
            .forEach { assertEquals(it, imageMimeType("x.${imageExtension(it)}")) }
        assertEquals("jpg", imageExtension(null))
    }
}
