import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.MessageDigest

fun main() {
    val url = URI.create("http://127.0.0.1:8080/").toURL()
    try {
        val fileBytes = downloadFullFile(url)
        println("Downloaded ${fileBytes.size} bytes.")
        println("SHA-256: ${sha256(fileBytes)}")
    } catch (e: IOException) {
        println("Error: Could not connect to server — ${e.message}")
    } catch (e: Exception) {
        println("Unexpected error: ${e.message}")
    }
}

fun downloadFullFile(url: URL): ByteArray {
    val initialConn = try {
        url.openConnection() as HttpURLConnection
    } catch (e: IOException) {
        throw IOException("Failed to open connection to $url", e)
    }

    initialConn.requestMethod = "GET"
    val declaredSize = initialConn.getHeaderField("Content-Length")?.toIntOrNull()
    println("Declared total size: ${declaredSize ?: "unknown"}")

    val firstChunk = try {
        initialConn.inputStream.readAllBytes()
    } catch (e: IOException) {
        throw IOException("Failed to read initial chunk from $url", e)
    } finally {
        initialConn.disconnect()
    }

    val buffer = ByteArrayOutputStream()
    buffer.write(firstChunk)
    var downloaded = firstChunk.size

    var useDeclaredSize = declaredSize != null

    while (true) {
        if (useDeclaredSize && downloaded >= declaredSize!!) {
            val verifyPart = downloadRange(url, downloaded, declaredSize)
            if (verifyPart.isEmpty()) {
                println("Verified: no more data beyond declared size ($declaredSize bytes).")
                break
            } else {
                println("Warning: received unexpected ${verifyPart.size} extra bytes — ignoring declared size.")
                buffer.write(verifyPart)
                downloaded += verifyPart.size
                useDeclaredSize = false // switch to open-ended mode
                continue
            }
        }

        val part = downloadRange(url, downloaded, declaredSize)
        buffer.write(part)
        if (part.isEmpty()) {
            if (declaredSize != null && downloaded < declaredSize) {
                println("Warning: received 0 bytes at offset $downloaded, but expected $declaredSize — possible incomplete file!")
            } else {
                println("Received 0 bytes at offset $downloaded — assuming end of file")
            }
            break
        }
        downloaded += part.size
    }

    return buffer.toByteArray()
}

fun downloadRange(url: URL, start: Int, declaredSize: Int?): ByteArray {
    return try {
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("Range", "bytes=$start-")
        conn.requestMethod = "GET"
        val bytes = conn.inputStream.readAllBytes()
        if (bytes.isEmpty()) {
            // warning handled in caller
        } else {
            val newTotal = start + bytes.size
            println("Received ${bytes.size} bytes (${declaredSize?.let { "$newTotal/$it" } ?: "$newTotal/?"})")
        }
        bytes
    } catch (e: IOException) {
        throw IOException("Failed to download range starting at byte $start", e)
    }
}

// Computes SHA-256 hash as a hex string
fun sha256(data: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(data)
    return digest.joinToString("") { "%02x".format(it) }
}
