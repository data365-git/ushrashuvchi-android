package com.example.audio

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.File
import java.text.Normalizer

class RecordingFileManager(context: Context) {

    val root: File = File(context.getExternalFilesDir(null), "Recordings").also { it.mkdirs() }

    private val moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val sidecarAdapter = moshi.adapter(RecordingSidecar::class.java)

    fun folderDir(slug: String): File = File(root, slug).also { it.mkdirs() }

    fun newRecordingFile(folderSlug: String, topic: String, createdAt: Long): File {
        val dir = folderDir(folderSlug)
        val safeSlug = slugify(topic)
        val name = "${createdAt}_${safeSlug}.m4a"
        return File(dir, name)
    }

    fun sidecarFor(audioFile: File): File {
        val nameWithoutExt = audioFile.nameWithoutExtension
        return File(audioFile.parentFile, "$nameWithoutExt.json")
    }

    fun writeSidecar(audioFile: File, sidecar: RecordingSidecar) {
        sidecarFor(audioFile).writeText(sidecarAdapter.toJson(sidecar))
    }

    fun readSidecar(audioFile: File): RecordingSidecar? {
        val file = sidecarFor(audioFile)
        if (!file.exists()) return null
        return try {
            sidecarAdapter.fromJson(file.readText())
        } catch (_: Exception) {
            null
        }
    }

    fun moveToTrash(audioFile: File) {
        val trashDir = folderDir(".trash")
        val dest = File(trashDir, audioFile.name)
        audioFile.renameTo(dest)
        val sidecar = sidecarFor(audioFile)
        if (sidecar.exists()) sidecar.renameTo(File(trashDir, sidecar.name))
    }

    fun restoreFromTrash(name: String, folderSlug: String): File? {
        val trashFile = File(folderDir(".trash"), name)
        if (!trashFile.exists()) return null
        val dest = File(folderDir(folderSlug), name)
        trashFile.renameTo(dest)
        val sidecarName = "${trashFile.nameWithoutExtension}.json"
        val trashSidecar = File(folderDir(".trash"), sidecarName)
        if (trashSidecar.exists()) trashSidecar.renameTo(File(folderDir(folderSlug), sidecarName))
        return dest
    }

    fun rescanForOrphans(): List<File> {
        return root.walkTopDown()
            .filter { it.isFile && it.extension == "m4a" }
            .toList()
    }

    private fun slugify(input: String): String {
        val normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "")
        val slug = normalized.lowercase()
            .replace("[^a-z0-9]+".toRegex(), "-")
            .trim('-')
        return if (slug.isEmpty()) "recording" else slug.take(50)
    }
}
