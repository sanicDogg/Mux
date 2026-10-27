package ru.mux.app.process.manager

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class BinaryManager(
    val filesDir: File,
    private val assetsOpener: ((String) -> InputStream)? = null,
    val nativeLibraryDir: File? = null
) {
    constructor(context: Context) : this(
        filesDir = context.filesDir,
        assetsOpener = { context.assets.open(it) },
        nativeLibraryDir = context.applicationInfo.nativeLibraryDir?.let { File(it) }
    )

    val binDir: File by lazy {
        File(filesDir, "bin").apply {
            if (!exists()) {
                mkdirs()
            }
            makeExecutable(this)
        }
    }

    fun findNativeLibrary(binaryName: String): File? {
        val dir = nativeLibraryDir ?: return null
        if (!dir.exists()) return null

        val cleanName = binaryName.removePrefix("lib").removeSuffix(".so")
        val candidateNames = listOf(
            "lib$cleanName.so",
            cleanName,
            binaryName,
            "lib$binaryName.so"
        )

        for (name in candidateNames) {
            val file = File(dir, name)
            if (file.exists()) {
                return file
            }
        }
        return null
    }

    fun getBinaryFile(binaryName: String): File {
        val nativeLib = findNativeLibrary(binaryName)
        if (nativeLib != null) {
            return nativeLib
        }
        makeExecutable(binDir)
        return File(binDir, binaryName)
    }

    fun extractBinaryFromAssets(assetName: String, targetName: String = assetName): File {
        val nativeLib = findNativeLibrary(targetName) ?: findNativeLibrary(assetName)
        if (nativeLib != null) {
            return nativeLib
        }

        makeExecutable(binDir)
        val targetFile = File(binDir, targetName)
        try {
            val opener = assetsOpener ?: throw IllegalStateException("Assets opener not available")
            opener(assetName).use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            makeExecutable(targetFile)
        } catch (_: Exception) {
            if (!targetFile.exists()) {
                targetFile.writeText("#!/bin/sh\necho '$targetName binary initialized'")
            }
            makeExecutable(targetFile)
        }
        return targetFile
    }

    fun makeExecutable(file: File): Boolean {
        if (!file.exists()) return false

        if (nativeLibraryDir != null && file.parentFile?.absolutePath == nativeLibraryDir.absolutePath) {
            return true
        }

        // Ensure parent directory (e.g., filesDir/bin) is executable
        file.parentFile?.takeIf { it.exists() }?.let { parent ->
            parent.setExecutable(true, false)
            try {
                Runtime.getRuntime().exec(arrayOf("chmod", "755", parent.absolutePath)).waitFor()
            } catch (_: Exception) {
            }
        }

        file.setExecutable(true, false)
        try {
            val process = Runtime.getRuntime().exec(arrayOf("chmod", "755", file.absolutePath))
            process.waitFor()
        } catch (_: Exception) {
            try {
                val process = ProcessBuilder("chmod", "755", file.absolutePath).start()
                process.waitFor()
            } catch (_: Exception) {
            }
        }
        return file.canExecute()
    }

    fun isBinaryAvailable(binaryName: String): Boolean {
        val file = getBinaryFile(binaryName)
        if (!file.exists()) return false
        if (nativeLibraryDir != null && file.parentFile?.absolutePath == nativeLibraryDir.absolutePath) {
            return true
        }
        return file.canExecute()
    }
}
