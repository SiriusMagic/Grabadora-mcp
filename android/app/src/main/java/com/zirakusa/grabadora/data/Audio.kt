package com.zirakusa.grabadora.data

/** How rec_audio hands out a recording (pure, with tests): .m4a in parts small enough for one relay message, even as base64. */
object Audio {
    const val MIME = "audio/mp4"
    /** 256 KB per part: about 342 KB as base64, well under a relay message and the hub's bridge. */
    const val PART = 256 * 1024

    /** How many parts a file of [bytes] has (at least one, so an empty file still answers). */
    fun parts(bytes: Long): Long = if (bytes <= 0) 1 else (bytes + PART - 1) / PART
}
