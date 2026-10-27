package ru.mux.app.process

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ru.mux.app.process.manager.BinaryManager
import java.io.ByteArrayInputStream
import java.io.File

class BinaryManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var binaryManager: BinaryManager

    @Before
    fun setup() {
        tempFolder.create()
        binaryManager = BinaryManager(
            filesDir = tempFolder.root,
            assetsOpener = { ByteArrayInputStream("echo test".toByteArray()) }
        )
    }

    @Test
    fun `binDir creates directory and ensures execution permissions`() {
        val binDir = binaryManager.binDir
        assertTrue(binDir.exists())
        assertTrue(binDir.isDirectory)
        assertTrue(binDir.canExecute())
    }

    @Test
    fun `makeExecutable sets execution permission on file and parent directory`() {
        val dummyFile = File(binaryManager.binDir, "dummy_bin")
        dummyFile.writeText("#!/bin/sh\necho hi")

        val executable = binaryManager.makeExecutable(dummyFile)
        assertTrue(executable)
        assertTrue(dummyFile.canExecute())
        assertTrue(binaryManager.binDir.canExecute())
    }

    @Test
    fun `extractBinaryFromAssets extracts file and sets execution rights`() {
        val binary = binaryManager.extractBinaryFromAssets("nginx", "nginx")
        assertTrue(binary.exists())
        assertTrue(binary.canExecute())
        assertTrue(binary.parentFile?.canExecute() == true)
    }

    @Test
    fun `getBinaryFile prefers nativeLibraryDir file when present`() {
        val nativeLibFolder = tempFolder.newFolder("lib")
        val nativeLibFile = File(nativeLibFolder, "libnginx.so").apply { writeText("mock binary") }

        val bm = BinaryManager(
            filesDir = tempFolder.root,
            assetsOpener = { ByteArrayInputStream("echo test".toByteArray()) },
            nativeLibraryDir = nativeLibFolder
        )

        val resolved = bm.getBinaryFile("nginx")
        assertEquals(nativeLibFile.absolutePath, resolved.absolutePath)
        assertTrue(bm.isBinaryAvailable("nginx"))
    }
}
