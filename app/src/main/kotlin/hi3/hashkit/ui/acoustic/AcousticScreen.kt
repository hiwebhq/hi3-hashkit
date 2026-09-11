package hi3.hashkit.ui.acoustic

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import hi3.hashkit.domain.acoustic.AcousticAnalyzer
import hi3.hashkit.ui.theme.HiBrand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val SAMPLE_RATE = 44_100
private const val RECORD_SECONDS = 3

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcousticScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var recording by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<AcousticAnalyzer.Result?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    fun startRecording() {
        if (recording) return
        recording = true
        result = null
        message = null
        scope.launch {
            val samples = withContext(Dispatchers.IO) { recordSamples() }
            recording = false
            if (samples == null || samples.isEmpty()) {
                message = "Couldn't capture audio. Check the microphone permission and try again."
                return@launch
            }
            val analysis = AcousticAnalyzer.analyze(samples, SAMPLE_RATE)
            if (analysis == null) message = "Clip too short to analyse." else result = analysis
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startRecording()
        else message = "Microphone permission is needed to listen to the fans."
    }

    fun onRecordClick() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) startRecording() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Acoustic fan check", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = HiBrand.background),
            )
        },
        containerColor = HiBrand.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Hold the phone's mic within a few cm of the miner's fans, keep the room quiet, " +
                        "and record a $RECORD_SECONDS-second clip. The audio is analysed on-device " +
                        "(FFT) and never saved or sent. This is a best-effort, indicative check — " +
                        "not a calibrated diagnostic.",
                    style = MaterialTheme.typography.bodySmall,
                    color = HiBrand.textSecondary,
                )
            }
            item {
                Button(onClick = ::onRecordClick, enabled = !recording, modifier = Modifier.fillMaxWidth()) {
                    Text(if (recording) "Listening…" else "Record $RECORD_SECONDS s")
                }
            }
            if (recording) {
                item { CircularProgressIndicator(modifier = Modifier.padding(4.dp)) }
            }

            result?.let { r ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (r.healthy) HiBrand.statusOnline.copy(alpha = 0.12f)
                            else HiBrand.statusDegraded.copy(alpha = 0.15f),
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                if (r.healthy) "No obvious fault" else "Check the fans",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (r.healthy) HiBrand.statusOnline else HiBrand.statusDegraded,
                            )
                            r.findings.forEach { f ->
                                Text("• $f", style = MaterialTheme.typography.bodyMedium, color = HiBrand.textPrimary,
                                    modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = HiBrand.surface),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text("MEASURED", style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary)
                            Text("Dominant tone: ${r.dominantHz.toInt()} Hz (≈ ${r.rpmEstimate} RPM if fundamental)",
                                style = MaterialTheme.typography.bodyMedium, color = HiBrand.textPrimary)
                            Text("Tonality: ${"%.1f".format(r.tonalRatio)}× · high-freq energy: ${"%.0f".format(r.highFreqRatio * 100)}%",
                                style = MaterialTheme.typography.bodyMedium, color = HiBrand.textPrimary)
                        }
                    }
                }
            }

            message?.let { msg ->
                item { Text(msg, style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary) }
            }
        }
    }
}

/** Record ~[RECORD_SECONDS]s of mono PCM and return normalised float samples, or null on failure. */
@SuppressLint("MissingPermission")
private fun recordSamples(): FloatArray? = runCatching {
    val minBuf = AudioRecord.getMinBufferSize(
        SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
    )
    if (minBuf <= 0) return null
    val total = SAMPLE_RATE * RECORD_SECONDS
    val out = ShortArray(total)
    val recorder = AudioRecord(
        MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
        AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        maxOf(minBuf, total * 2),
    )
    if (recorder.state != AudioRecord.STATE_INITIALIZED) {
        recorder.release()
        return null
    }
    try {
        recorder.startRecording()
        var read = 0
        while (read < total) {
            val r = recorder.read(out, read, total - read)
            if (r <= 0) break
            read += r
        }
        if (read < total / 2) return null
        FloatArray(read) { out[it] / 32768f }
    } finally {
        runCatching { recorder.stop() }
        recorder.release()
    }
}.getOrNull()
