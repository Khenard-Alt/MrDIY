package com.mrdiy.careers.data.repository

import java.io.InputStream

object ResumeFiles {
    const val MAX_BYTES = 10 * 1024 * 1024
    // The deployed Supabase project uses the existing `resumes` bucket.
    // Keep ownership enforced by the first path segment (the authenticated UID).
    const val BUCKET = "resumes"
    fun readBounded(input: InputStream): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= MAX_BYTES)
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
    private const val DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

    fun extension(name: String, mime: String?, bytes: ByteArray): String {
        require(bytes.isNotEmpty())
        val pdf = bytes.take(5).toByteArray().toString(Charsets.US_ASCII) == "%PDF-"
        // DOCX files are ZIP archives — detect via the "PK\x03\x04" local-file-header signature.
        val zip = bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() &&
            bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()
        if (name.endsWith(".pdf", true) && pdf && mime in listOf(null, "application/pdf", "application/octet-stream")) return "pdf"
        if (name.endsWith(".docx", true) && zip && mime in listOf(null, DOCX_MIME, "application/octet-stream") && isWordDocx(bytes)) return "docx"
        if (name.endsWith(".txt", true) && !pdf && !zip && !bytes.contains(0.toByte()) && mime in listOf(null, "text/plain", "application/octet-stream")) return "txt"
        throw IllegalArgumentException("Unsupported file")
    }

    // Confirms the ZIP actually contains a Word document part, not just any .docx-named ZIP.
    private fun isWordDocx(bytes: ByteArray): Boolean {
        return try {
            java.util.zip.ZipInputStream(bytes.inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == "word/document.xml") return true
                    entry = zip.nextEntry
                }
                false
            }
        } catch (e: Exception) {
            false
        }
    }
    fun ownedPath(path: String, uid: String): Boolean = uid.isNotBlank() &&
        path.startsWith("$uid/") && path.split('/').size == 2 &&
        !path.contains("..") && !path.contains('\\') && !path.contains('%')
}
