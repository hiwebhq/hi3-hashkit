package hi3.hashkit.ui.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * The app's outbound-intent helpers. Every site used to hand-roll these (15 call sites,
 * with the FileProvider authority typed in five places and runCatching applied to some
 * but not others) — a device with no handler app must never crash the caller.
 */

/** Open [url] in the user's browser; silently a no-op if nothing can handle it. */
fun Context.openUrl(url: String) {
    runCatching {
        startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/** Show the system chooser for [intent]; safe when no app can handle it. */
fun Context.launchChooser(intent: Intent, title: String) {
    runCatching {
        startActivity(Intent.createChooser(intent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun Context.providerUri(file: File): Uri =
    FileProvider.getUriForFile(this, "$packageName.files", file)

/** Share [file] via the system share sheet (FileProvider-backed). */
fun Context.shareFile(file: File, mime: String, title: String) {
    runCatching {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, providerUri(file))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        launchChooser(send, title)
    }
}

/** Open [file] in an external viewer app (e.g. the bundled PDF guide). */
fun Context.viewFile(file: File, mime: String) {
    runCatching {
        startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(providerUri(file), mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }
}
