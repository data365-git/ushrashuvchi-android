package com.example.e2e.support

import java.io.File

class FakeRecordingHarness(private val tempDir: File) {
    fun writeFixtureAudio(filename: String, sizeBytes: Long = 9_000): File {
        val file = File(tempDir, filename)
        file.parentFile?.mkdirs()
        // Minimal M4A-like bytes (not a real M4A, but enough for tests that don't probe).
        // Header magic + padding.
        val header = byteArrayOf(
            0x00, 0x00, 0x00, 0x20, 0x66, 0x74, 0x79, 0x70, // size + 'ftyp'
            0x6D, 0x70, 0x34, 0x32, 0x00, 0x00, 0x00, 0x00, // 'mp42' + minor version
            0x69, 0x73, 0x6F, 0x6D, 0x6D, 0x70, 0x34, 0x32, // 'isom' 'mp42'
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        )
        file.writeBytes(header)
        if (sizeBytes > header.size) {
            file.appendBytes(ByteArray((sizeBytes - header.size).toInt()))
        }
        return file
    }
}
