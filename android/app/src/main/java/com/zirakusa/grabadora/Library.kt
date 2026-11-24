package com.zirakusa.grabadora

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.core.net.toUri
import com.zirakusa.grabadora.data.KeyValues
import com.zirakusa.grabadora.data.MetaStore
import com.zirakusa.grabadora.data.Names
import com.zirakusa.grabadora.data.Recording
import com.zirakusa.grabadora.data.Search
import java.io.File
import java.util.zip.CRC32

/**
 * The recordings on the phone. Android 10+: MediaStore Audio in Music/Grabadora (the app's own entries need no
 * permission; with «Grabaciones anteriores» it also sees the ones made before reinstalling). Android 8–9: the app's
 * own music folder, which needs no permission either. Flags and lengths are kept in [MetaStore].
 */
class Library(private val context: Context) {
    private val resolver get() = context.contentResolver
    private val meta = MetaStore(SharedValues(context))
    private val modern = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    /** Where a new recording is being written: a pending MediaStore entry, or a file. */
    class Output(val uri: Uri?, val file: File?, val descriptor: ParcelFileDescriptor?, val name: String)

    fun list(): List<Recording> = Search.sorted(runCatching { if (modern) store() else folder() }.getOrDefault(emptyList()))

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun collection(): Uri = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun store(): List<Recording> {
        val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.OWNER_PACKAGE_NAME)
        val list = ArrayList<Recording>()
        resolver.query(collection(), projection, "${MediaStore.Audio.Media.RELATIVE_PATH} = ?", arrayOf(Names.FOLDER + "/"), null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val file = c.getString(1) ?: continue
                val duration = c.getLong(3).takeIf { it > 0 } ?: meta.duration(id)
                list += Recording(id, Names.of(file), file, c.getLong(2) * 1000, duration, c.getLong(4), Names.FOLDER, meta.markers(id),
                    ContentUris.withAppendedId(collection(), id).toString(), own = c.getString(5) == context.packageName)
            }
        }
        return list
    }

    private fun dir(): File = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: File(context.filesDir, "Music"), "Grabadora")
        .apply { mkdirs() }
    private fun idOf(file: File) = CRC32().apply { update(file.name.toByteArray()) }.value
    private fun place() = "Android/data/${context.packageName}/files/${Names.FOLDER}"

    private fun folder(): List<Recording> = dir().listFiles { f -> f.isFile && f.name.endsWith(Names.EXTENSION, true) }.orEmpty().map { f ->
        val id = idOf(f)
        Recording(id, Names.of(f.name), f.name, f.lastModified(), meta.duration(id).takeIf { it > 0 } ?: probe(f), f.length(), place(),
            meta.markers(id), Uri.fromFile(f).toString())
    }

    private fun probe(f: File): Long = runCatching {
        MediaMetadataRetriever().run { try { setDataSource(f.path); extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() } finally { release() } }
    }.getOrNull() ?: 0L

    /** A new recording called [name] (or «name (2)»…). */
    fun create(name: String): Output {
        val unique = Names.unique(name, list().map { it.name })
        if (!modern) return Output(null, File(dir(), Names.file(unique)), null, unique)
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, Names.file(unique)); put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
            put(MediaStore.Audio.Media.RELATIVE_PATH, Names.FOLDER + "/"); put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection(), values) ?: error("MediaStore gave no entry")
        val descriptor = resolver.openFileDescriptor(uri, "w") ?: run { resolver.delete(uri, null, null); error("No file") }
        return Output(uri, null, descriptor, unique)
    }

    /** The recording is complete: it shows in the phone's music folder, with its flags, length and waveform kept. */
    fun finish(out: Output, durationMs: Long, markers: List<Long>, levels: FloatArray) {
        out.descriptor?.close()
        val id = if (out.uri != null) {
            if (modern) resolver.update(out.uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
            ContentUris.parseId(out.uri)
        } else idOf(out.file!!)
        meta.save(id, durationMs, markers)
        Waves.save(context, id, levels)
    }

    /** Recording failed or was too short: nothing is left behind. */
    fun abandon(out: Output) {
        runCatching { out.descriptor?.close() }
        out.uri?.let { runCatching { resolver.delete(it, null, null) } }
        out.file?.delete()
    }

    /** Renames to [name] (clean and unique). Throws SecurityException when Android must ask the person first. */
    fun rename(r: Recording, name: String): Recording {
        if (modern) {
            val values = ContentValues().apply { put(MediaStore.Audio.Media.DISPLAY_NAME, Names.file(name)); put(MediaStore.Audio.Media.TITLE, name) }
            check(resolver.update(r.uri.toUri(), values, null, null) > 0)
            return r.copy(name = name, fileName = Names.file(name))
        }
        val to = File(dir(), Names.file(name))
        check(!to.exists() && File(dir(), r.fileName).renameTo(to))
        val id = idOf(to)
        meta.move(r.id, id); Waves.move(context, r.id, id)
        return r.copy(id = id, name = name, fileName = to.name, uri = Uri.fromFile(to).toString())
    }

    /**
     * Android 11+: to the phone's trash (it can be restored for 30 days). Android 10: deleted. Android 8–9: the file is
     * deleted. Throws SecurityException for a recording this install did not make.
     */
    fun delete(r: Recording): Boolean {
        if (!modern) return File(dir(), r.fileName).delete().also { if (it) { meta.forget(r.id); Waves.forget(context, r.id) } }
        val uri = r.uri.toUri()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_TRASHED, 1) }, null, null) > 0
        else (resolver.delete(uri, null, null) > 0).also { if (it) { meta.forget(r.id); Waves.forget(context, r.id) } }
    }

    /** Android 11+: the system's own «move to trash?» for recordings this install did not make. */
    @RequiresApi(Build.VERSION_CODES.R)
    fun trashRequest(list: List<Recording>): IntentSender = MediaStore.createTrashRequest(resolver, list.map { it.uri.toUri() }, true).intentSender

    /** What another app gets to read the file (sharing): the MediaStore entry, or the file through our FileProvider. */
    fun shareUri(r: Recording): Uri = if (modern) r.uri.toUri()
        else FileProvider.getUriForFile(context, "${context.packageName}.files", File(dir(), r.fileName))
}

/** The phone's preferences file «grabadora» (kept out of backups). The service, the tools and the summary read the same. */
class SharedValues(context: Context) : KeyValues {
    private val prefs = context.applicationContext.getSharedPreferences("grabadora", Context.MODE_PRIVATE)
    override fun get(key: String): String? = prefs.getString(key, null)
    override fun put(key: String, value: String?) {
        prefs.edit(commit = true) { if (value == null) remove(key) else putString(key, value) }
    }
}
