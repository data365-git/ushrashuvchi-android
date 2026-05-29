package com.example.server.video

import java.io.File

class Transcoder {

    fun normalizeToMp4(input: File, output: File) {
        run(listOf(
            "ffmpeg", "-y", "-i", input.absolutePath,
            "-vf", "scale=-2:720",
            "-r", "30",
            "-c:v", "libx264",
            "-profile:v", "baseline",
            "-b:v", "1200k",
            "-maxrate", "1500k",
            "-bufsize", "2400k",
            "-c:a", "aac",
            "-b:a", "128k",
            "-movflags", "+faststart",
            output.absolutePath
        ))
    }

    fun extractAudio(mp4: File): File {
        val out = File(mp4.parentFile, "${mp4.nameWithoutExtension}.m4a")
        run(listOf(
            "ffmpeg", "-y", "-i", mp4.absolutePath,
            "-vn",
            "-c:a", "aac",
            "-b:a", "128k",
            out.absolutePath
        ))
        return out
    }

    fun normalizeAudio(input: File, output: File) {
        val codec = probeAudioCodec(input)
        if (codec == "aac") {
            run(listOf(
                "ffmpeg", "-y", "-i", input.absolutePath,
                "-c:a", "copy",
                "-movflags", "+faststart",
                output.absolutePath
            ))
        } else {
            run(listOf(
                "ffmpeg", "-y", "-i", input.absolutePath,
                "-vn",
                "-c:a", "aac",
                "-b:a", "96k",
                "-ar", "44100",
                "-movflags", "+faststart",
                output.absolutePath
            ))
        }
    }

    private fun probeAudioCodec(file: File): String {
        return try {
            val process = ProcessBuilder(listOf(
                "ffprobe", "-v", "error",
                "-select_streams", "a:0",
                "-show_entries", "stream=codec_name",
                "-of", "default=nokey=1:noprint_wrapper=1",
                file.absolutePath
            )).start()
            val stdout = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            stdout
        } catch (_: Exception) {
            "" // unknown → transcode path
        }
    }

    private fun run(cmd: List<String>) {
        val process = ProcessBuilder(cmd)
            .redirectErrorStream(true)
            .start()
        process.inputStream.bufferedReader().use { reader ->
            reader.lines().forEach { /* drain */ }
        }
        val exit = process.waitFor()
        require(exit == 0) { "ffmpeg failed (exit $exit): ${cmd.joinToString(" ")}" }
    }
}
