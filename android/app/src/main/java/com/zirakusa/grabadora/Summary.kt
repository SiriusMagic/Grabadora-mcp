package com.zirakusa.grabadora

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import com.zirakusa.grabadora.data.Clock
import com.zirakusa.grabadora.data.Recording
import com.zirakusa.grabadora.data.Search

/** The lines of Grabadora's card in Inicio: recording now, how many there are and the newest. Pure; texts come in. */
object SummaryLines {
    class Texts(val recording: String, val paused: String, val last: String, val count: (Int) -> String)

    fun lines(list: List<Recording>, status: RecStatus, elapsedMs: Long, t: Texts): List<String> {
        val now = when (status) {
            RecStatus.RECORDING -> t.recording.format(Clock.timer(elapsedMs))
            RecStatus.PAUSED -> t.paused.format(Clock.timer(elapsedMs))
            RecStatus.IDLE -> null
        }
        val newest = Search.sorted(list).firstOrNull()
        return listOfNotNull(now, list.takeIf { it.isNotEmpty() }?.let { t.count(it.size) },
            newest?.let { t.last.format(it.name, Clock.duration(it.durationMs)) })
    }
}

/**
 * The family summary (see Inicio's FAMILIA.md): what Inicio shows in its «Hoy» card for Grabadora. Only apps signed
 * by the same developer may ask (Android's signature permission and the caller's signature checked here). Only names
 * and lengths: never the audio or where it is.
 */
class SummaryProvider : ContentProvider() {
    override fun onCreate() = true
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method != "summary") return null
        val context = context ?: return null
        val caller = callingPackage ?: return null
        if (context.packageManager.checkSignatures(context.packageName, caller) != PackageManager.SIGNATURE_MATCH) return null
        val texts = SummaryLines.Texts(context.getString(R.string.summary_recording), context.getString(R.string.summary_paused),
            context.getString(R.string.summary_last)) { n -> context.resources.getQuantityString(R.plurals.recordings, n, n) }
        val lines = SummaryLines.lines(Library(context).list(), Session.status, Session.elapsed, texts)
        return Bundle().apply {
            putString("title", context.getString(R.string.app_name))
            putStringArrayList("lines", ArrayList(lines))
            putString("empty", context.getString(R.string.summary_empty))
        }
    }
    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
}
