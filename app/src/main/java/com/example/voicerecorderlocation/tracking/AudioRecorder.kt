package com.example.voicerecorderlocation.tracking

import android.media.MediaRecorder
import java.io.File

class AudioRecorder {
    private var recorder: MediaRecorder? = null

    fun start(outputFile: File) {
        outputFile.parentFile?.mkdirs()
        @Suppress("DEPRECATION")
        val mediaRecorder = MediaRecorder()
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        mediaRecorder.setAudioEncodingBitRate(128_000)
        mediaRecorder.setAudioSamplingRate(44_100)
        mediaRecorder.setOutputFile(outputFile.absolutePath)
        mediaRecorder.prepare()
        mediaRecorder.start()
        recorder = mediaRecorder
    }

    fun stop() {
        val current = recorder ?: return
        runCatching { current.stop() }
        current.reset()
        current.release()
        recorder = null
    }
}
