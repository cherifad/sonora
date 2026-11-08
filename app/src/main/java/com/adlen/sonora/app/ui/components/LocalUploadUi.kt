// Sonora (https://github.com/cherifad/sonora), a fork of Myspoti (https://github.com/kota-zo/myspoti), GPL-3.0.
package com.adlen.sonora.app.ui.components

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.adlen.sonora.app.data.local.LocalMusic
import com.adlen.sonora.app.data.local.LocalTrack
import com.adlen.sonora.app.data.upload.LocalUploadIndex
import com.adlen.sonora.app.data.upload.UploadEntry
import com.adlen.sonora.app.data.upload.UploadManager
import com.adlen.sonora.app.data.upload.UploadOrigin
import com.adlen.sonora.app.data.upload.UploadState
import com.adlen.sonora.engine.R
import kotlinx.coroutines.launch

/** The badge of a local file against the server, shared by both looks. */
@Composable
fun uploadStateLabel(entry: UploadEntry): String = when (entry.state) {
    UploadState.QUEUED -> stringResource(R.string.upload_state_queued)
    UploadState.UPLOADING -> stringResource(R.string.upload_state_uploading)
    UploadState.WAITING_SCAN -> stringResource(R.string.upload_state_scan)
    UploadState.UPLOADED -> stringResource(
        if (entry.origin == UploadOrigin.DUPLICATE) R.string.upload_state_duplicate else R.string.upload_state_uploaded,
    )
    UploadState.NEEDS_REVIEW -> stringResource(R.string.upload_state_review)
    UploadState.SKIPPED -> stringResource(R.string.upload_state_skipped)
    UploadState.FAILED -> stringResource(R.string.upload_state_failed)
}

/** What a screen needs from the delete flow: whether it runs, and how to start it. */
data class LocalDeleteHandle(val deleting: Boolean, val request: () -> Unit)

/**
 * The confirmation dialog and the system MediaStore delete request, shared by both looks.
 * [candidates] are the `UPLOADED` files; each is verified against the server before the
 * system dialog is opened, so nothing unconfirmed is ever offered for deletion.
 */
@Composable
fun LocalDeleteFlow(
    candidates: List<LocalTrack>,
    uploads: Map<String, UploadEntry>,
    showDialog: Boolean,
    onDismiss: () -> Unit,
    onDeleted: (List<LocalTrack>) -> Unit,
): LocalDeleteHandle {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var deleting by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<List<LocalTrack>>(emptyList()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val removed = pending
        pending = emptyList()
        if (result.resultCode == Activity.RESULT_OK && removed.isNotEmpty()) {
            onDeleted(removed)
            Toast.makeText(
                context,
                context.getString(R.string.local_files_delete_done, removed.size),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    val request: () -> Unit = {
        if (!deleting) {
            deleting = true
            scope.launch {
                try {
                    val verified = candidates.filter { track ->
                        UploadManager.isOnServer(uploads[LocalUploadIndex.key(track)]?.serverTrackId)
                    }
                    if (verified.isEmpty()) {
                        Toast.makeText(context, R.string.local_files_delete_none, Toast.LENGTH_SHORT).show()
                    } else {
                        // MediaStore refuses a delete request with more than 2000 URIs.
                        val intent = LocalMusic.deleteRequest(context, verified.take(2000))
                        if (intent == null) {
                            Toast.makeText(context, R.string.local_files_delete_unsupported, Toast.LENGTH_SHORT).show()
                        } else {
                            pending = verified
                            launcher.launch(IntentSenderRequest.Builder(intent.intentSender).build())
                        }
                    }
                } finally {
                    deleting = false
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.local_files_delete_title)) },
            text = {
                Text(pluralStringResource(R.plurals.local_files_delete_message, candidates.size, candidates.size))
            },
            confirmButton = {
                TextButton(onClick = { onDismiss(); request() }) {
                    Text(stringResource(R.string.local_files_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.local_files_delete_cancel))
                }
            },
        )
    }
    return LocalDeleteHandle(deleting = deleting, request = request)
}
