package com.example.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Random

class StorageSpeedTester(private val context: Context) {

    data class StorageSpeedResult(
        val writeSpeedMBps: Double = 0.0,
        val readSpeedMBps: Double = 0.0,
        val testFileSizeBytes: Long = 0,
        val isSuccess: Boolean = false,
        val message: String = ""
    )

    suspend fun runSpeedTest(): StorageSpeedResult = withContext(Dispatchers.IO) {
        val testFile = File(context.filesDir, "speed_benchmark_tmp.bin")
        val sizeMb = 12 // 12 MB test block
        val totalBytes = sizeMb * 1024 * 1024
        val buffer = ByteArray(64 * 1024) // 64 KB buffer
        Random().nextBytes(buffer)

        var writeSpeed = 0.0
        var readSpeed = 0.0

        try {
            // Write Test
            val writeStart = System.nanoTime()
            FileOutputStream(testFile).use { fos ->
                var written = 0
                while (written < totalBytes) {
                    val toWrite = minOf(buffer.size, totalBytes - written)
                    fos.write(buffer, 0, toWrite)
                    written += toWrite
                }
                fos.fd.sync()
            }
            val writeEnd = System.nanoTime()
            val writeSeconds = (writeEnd - writeStart) / 1_000_000_000.0
            if (writeSeconds > 0) {
                writeSpeed = (totalBytes / (1024.0 * 1024.0)) / writeSeconds
            }

            // Read Test
            val readBuffer = ByteArray(64 * 1024)
            val readStart = System.nanoTime()
            FileInputStream(testFile).use { fis ->
                var readTotal = 0
                while (readTotal < totalBytes) {
                    val read = fis.read(readBuffer)
                    if (read == -1) break
                    readTotal += read
                }
            }
            val readEnd = System.nanoTime()
            val readSeconds = (readEnd - readStart) / 1_000_000_000.0
            if (readSeconds > 0) {
                readSpeed = (totalBytes / (1024.0 * 1024.0)) / readSeconds
            }

            // Clean up
            testFile.delete()

            StorageSpeedResult(
                writeSpeedMBps = (writeSpeed * 10).toLong() / 10.0,
                readSpeedMBps = (readSpeed * 10).toLong() / 10.0,
                testFileSizeBytes = totalBytes.toLong(),
                isSuccess = true,
                message = "Internal UFS/eMMC Flash Tested Successfully"
            )
        } catch (e: Exception) {
            testFile.delete()
            StorageSpeedResult(
                isSuccess = false,
                message = "Benchmark failed: ${e.localizedMessage}"
            )
        }
    }
}
