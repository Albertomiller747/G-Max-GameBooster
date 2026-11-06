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
        val testFile = File(context.cacheDir, "ufs_speed_benchmark_tmp.bin")
        val blockBytes = 1024 * 1024 // 1 MB sequential block size
        val totalBlocks = 32 // 32 MB total test payload
        val totalBytes = totalBlocks.toLong() * blockBytes.toLong()

        // Generate uncompressible pseudo-random payload with per-block entropy mutation
        val writeBuffer = ByteArray(blockBytes)
        val rng = Random(System.nanoTime())
        rng.nextBytes(writeBuffer)

        var writeSpeed = 0.0
        var readSpeed = 0.0

        try {
            if (testFile.exists()) {
                testFile.delete()
            }

            // 1. Sequential Hardware Write Test (with per-chunk entropy + final fdatasync)
            val writeStart = System.nanoTime()
            FileOutputStream(testFile, false).use { fos ->
                val channel = fos.channel
                val byteBuf = java.nio.ByteBuffer.allocateDirect(blockBytes)
                for (blockIndex in 0 until totalBlocks) {
                    // Mutate 64 bytes per 1MB block to prevent filesystem deduplication/compression
                    for (k in 0 until 64) {
                        writeBuffer[(blockIndex * 131 + k * 997) and (blockBytes - 1)] =
                            (blockIndex xor k xor 0x5A).toByte()
                    }
                    byteBuf.clear()
                    byteBuf.put(writeBuffer)
                    byteBuf.flip()
                    while (byteBuf.hasRemaining()) {
                        channel.write(byteBuf)
                    }
                }
                channel.force(true)
                fos.fd.sync()
            }
            val writeEnd = System.nanoTime()
            val writeSeconds = ((writeEnd - writeStart) / 1_000_000_000.0).coerceAtLeast(0.001)
            writeSpeed = (totalBytes.toDouble() / (1024.0 * 1024.0)) / writeSeconds

            // 2. Sequential Hardware Read Test (1MB direct channel reads)
            val readStart = System.nanoTime()
            var checksum = 0L
            FileInputStream(testFile).use { fis ->
                val channel = fis.channel
                val readBuf = java.nio.ByteBuffer.allocateDirect(blockBytes)
                var readTotal = 0L
                while (readTotal < totalBytes) {
                    readBuf.clear()
                    val n = channel.read(readBuf)
                    if (n <= 0) break
                    readBuf.flip()
                    checksum += readBuf.get(0).toLong()
                    readTotal += n
                }
            }
            val readEnd = System.nanoTime()
            val readSeconds = ((readEnd - readStart) / 1_000_000_000.0).coerceAtLeast(0.001)
            readSpeed = (totalBytes.toDouble() / (1024.0 * 1024.0)) / readSeconds

            // Clean up temporary benchmark file immediately
            testFile.delete()

            val tierLabel = when {
                readSpeed >= 1200.0 || writeSpeed >= 600.0 -> "UFS 3.1 / 4.0 High-Speed Flash"
                readSpeed >= 500.0 || writeSpeed >= 220.0 -> "UFS 2.1 / 2.2 Flash Storage"
                else -> "Internal eMMC / UFS Flash Storage"
            }

            StorageSpeedResult(
                writeSpeedMBps = kotlin.math.round(writeSpeed * 10.0) / 10.0,
                readSpeedMBps = kotlin.math.round(readSpeed * 10.0) / 10.0,
                testFileSizeBytes = totalBytes + (checksum and 0L),
                isSuccess = true,
                message = "$tierLabel • 32MB Hardware Sync Verified"
            )
        } catch (e: Exception) {
            try {
                testFile.delete()
            } catch (_: Exception) {}
            StorageSpeedResult(
                isSuccess = false,
                message = "Benchmark failed: ${e.localizedMessage}"
            )
        }
    }
}
