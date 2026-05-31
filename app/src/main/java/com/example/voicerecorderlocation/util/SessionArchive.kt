package com.example.voicerecorderlocation.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.example.voicerecorderlocation.data.LocationPointEntity
import com.example.voicerecorderlocation.data.PlaceMarkerEntity
import com.example.voicerecorderlocation.data.RecordingSessionEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class ImportedSessionArchive(
    val title: String,
    val audioFile: File,
    val startedAtMillis: Long,
    val endedAtMillis: Long?,
    val durationMillis: Long,
    val waveform: String?,
    val points: List<LocationPointEntity>,
    val markers: List<PlaceMarkerEntity>
)

fun shareZipFiles(context: Context, zipFiles: List<File>) {
    val uris = zipFiles.mapNotNull { file ->
        if (!file.exists()) return@mapNotNull null
        runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull()
    }
    if (uris.isEmpty()) return

    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = "application/zip"
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "分享录音档案"))
}

fun Context.exportSessionZip(
    session: RecordingSessionEntity,
    points: List<LocationPointEntity>,
    markers: List<PlaceMarkerEntity> = emptyList()
): File? {
    val audioFile = File(session.audioPath)
    if (!audioFile.exists()) return null

    return runCatching {
        val exportDirectory = File(
            getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: filesDir,
            "Exports"
        )
        exportDirectory.mkdirs()
        val safeTitle = session.title.sanitizedFileName().ifBlank { "recording-${session.id}" }
        val zipFile = File(exportDirectory, "$safeTitle-${session.id}.zip")
        val audioEntryName = "recording.${audioFile.extension.ifBlank { "m4a" }}"
        val metadata = sessionArchiveMetadata(session, points, markers, audioEntryName)
        val geoJson = sessionTrackGeoJson(session, points)

        ZipOutputStream(FileOutputStream(zipFile)).use { zip ->
            zip.putNextEntry(ZipEntry(audioEntryName))
            audioFile.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("metadata.json"))
            zip.write(metadata.toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("track.geojson"))
            zip.write(geoJson.toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        zipFile
    }.getOrNull()
}

fun Context.importSessionZip(uri: Uri): ImportedSessionArchive? = runCatching {
    var metadata: JSONObject? = null
    var audioFile: File? = null

    contentResolver.openInputStream(uri)?.use { input ->
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    when {
                        entry.name == "metadata.json" -> {
                            metadata = JSONObject(zip.readBytes().toString(Charsets.UTF_8))
                        }
                        entry.name.startsWith("recording.") -> {
                            val target = createImportedAudioFile(entry.name)
                            FileOutputStream(target).use { output -> zip.copyTo(output) }
                            audioFile = target
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    val archiveMetadata = metadata ?: return null
    val importedAudioFile = audioFile ?: return null
    ImportedSessionArchive(
        title = archiveMetadata.optString("title", importedAudioFile.nameWithoutExtension),
        audioFile = importedAudioFile,
        startedAtMillis = archiveMetadata.optLong("startedAtMillis", System.currentTimeMillis()),
        endedAtMillis = archiveMetadata.nullableLong("endedAtMillis"),
        durationMillis = archiveMetadata.optLong("durationMillis", 0L),
        waveform = if (archiveMetadata.isNull("waveform")) null else archiveMetadata.optString("waveform").ifBlank { null },
        points = archiveMetadata.pointsFromMetadata(),
        markers = archiveMetadata.markersFromMetadata()
    )
}.getOrNull()

private fun Context.createImportedAudioFile(displayName: String): File {
    val directory = getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: filesDir
    directory.mkdirs()
    val cleanName = displayName
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
        .ifBlank { "imported-recording.m4a" }
    return File(directory, "${UUID.randomUUID()}-$cleanName")
}

private fun sessionArchiveMetadata(
    session: RecordingSessionEntity,
    points: List<LocationPointEntity>,
    markers: List<PlaceMarkerEntity>,
    audioEntryName: String
): JSONObject =
    JSONObject()
        .put("format", "voice-recorder-with-location")
        .put("version", 2)
        .put("title", session.title)
        .put("audioEntry", audioEntryName)
        .put("startedAtMillis", session.startedAtMillis)
        .put("endedAtMillis", session.endedAtMillis ?: JSONObject.NULL)
        .put("durationMillis", session.durationMillis)
        .put("waveform", session.waveform ?: JSONObject.NULL)
        .put("points", JSONArray(points.map { it.toJson() }))
        .put("markers", JSONArray(markers.map { it.toJson() }))

private fun sessionTrackGeoJson(
    session: RecordingSessionEntity,
    points: List<LocationPointEntity>
): JSONObject =
    JSONObject()
        .put("type", "FeatureCollection")
        .put(
            "features",
            JSONArray(
                listOf(
                    JSONObject()
                        .put("type", "Feature")
                        .put(
                            "properties",
                            JSONObject()
                                .put("title", session.title)
                                .put("startedAtMillis", session.startedAtMillis)
                        )
                        .put(
                            "geometry",
                            JSONObject()
                                .put("type", "LineString")
                                .put(
                                    "coordinates",
                                    JSONArray(points.map { JSONArray(listOf(it.longitude, it.latitude)) })
                                )
                        )
                )
            )
        )

private fun LocationPointEntity.toJson(): JSONObject =
    JSONObject()
        .put("latitude", latitude)
        .put("longitude", longitude)
        .put("accuracyMeters", accuracyMeters ?: JSONObject.NULL)
        .put("altitudeMeters", altitudeMeters ?: JSONObject.NULL)
        .put("speedMetersPerSecond", speedMetersPerSecond ?: JSONObject.NULL)
        .put("bearingDegrees", bearingDegrees ?: JSONObject.NULL)
        .put("recordedAtMillis", recordedAtMillis)
        .put("elapsedRealtimeNanos", elapsedRealtimeNanos)

private fun PlaceMarkerEntity.toJson(): JSONObject =
    JSONObject()
        .put("name", name)
        .put("latitude", latitude ?: JSONObject.NULL)
        .put("longitude", longitude ?: JSONObject.NULL)
        .put("elapsedMillis", elapsedMillis)
        .put("recordedAtMillis", recordedAtMillis)

private fun JSONObject.markersFromMetadata(): List<PlaceMarkerEntity> {
    val markers = optJSONArray("markers") ?: return emptyList()
    return List(markers.length()) { index ->
        val m = markers.getJSONObject(index)
        PlaceMarkerEntity(
            sessionId = 0,
            name = m.optString("name", "标记"),
            latitude = m.nullableDouble("latitude"),
            longitude = m.nullableDouble("longitude"),
            elapsedMillis = m.optLong("elapsedMillis", 0L),
            recordedAtMillis = m.optLong("recordedAtMillis", 0L)
        )
    }
}

private fun JSONObject.pointsFromMetadata(): List<LocationPointEntity> {
    val points = optJSONArray("points") ?: return emptyList()
    return List(points.length()) { index ->
        val point = points.getJSONObject(index)
        LocationPointEntity(
            sessionId = 0,
            latitude = point.getDouble("latitude"),
            longitude = point.getDouble("longitude"),
            accuracyMeters = point.nullableDouble("accuracyMeters")?.toFloat(),
            altitudeMeters = point.nullableDouble("altitudeMeters"),
            speedMetersPerSecond = point.nullableDouble("speedMetersPerSecond")?.toFloat(),
            bearingDegrees = point.nullableDouble("bearingDegrees")?.toFloat(),
            recordedAtMillis = point.getLong("recordedAtMillis"),
            elapsedRealtimeNanos = point.optLong("elapsedRealtimeNanos", 0L)
        )
    }
}

private fun JSONObject.nullableLong(name: String): Long? =
    if (isNull(name)) null else getLong(name)

private fun JSONObject.nullableDouble(name: String): Double? =
    if (isNull(name)) null else getDouble(name)

private fun String.sanitizedFileName(): String =
    replace(Regex("[^A-Za-z0-9._-]"), "_")
