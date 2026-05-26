package com.sye.tv

object DriveHelper {

    fun isDrive(url: String) = url.contains("drive.google.com") || url.contains("docs.google.com")

    /** Extract the file ID from any known Drive URL format. */
    fun fileId(url: String): String? {
        // /file/d/FILE_ID/...
        Regex("drive\\.google\\.com/file/d/([a-zA-Z0-9_-]+)").find(url)
            ?.let { return it.groupValues[1] }
        // ?id=FILE_ID or &id=FILE_ID
        Regex("[?&]id=([a-zA-Z0-9_-]+)").find(url)
            ?.let { return it.groupValues[1] }
        return null
    }

    /** Convert a Drive share URL to a direct download stream URL. */
    fun streamUrl(url: String): String {
        if (!isDrive(url)) return url
        val id = fileId(url) ?: return url
        // confirm=t bypasses the large-file virus-scan warning page
        return "https://drive.google.com/uc?export=download&id=$id&confirm=t"
    }
}
