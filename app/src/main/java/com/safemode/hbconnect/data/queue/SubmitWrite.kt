package com.safemode.hbconnect.data.queue

/** Message shown when a write was saved to the outbox instead of sent. */
const val OFFLINE_SAVED_MESSAGE = "Saved offline. It will upload when the server is reachable."

/**
 * Convenience over [UploadQueueManager.submit] for the common ViewModel write handler: runs the
 * write online (then [onSent], e.g. a reload) or saves it to the outbox, and reports the outcome
 * through the handler's usual `(success, message)` callback. A queued write is reported as a success
 * with an offline note; an online failure passes the server's message through.
 */
suspend fun UploadQueueManager.submitWrite(
    kind: OpKind,
    payload: Any?,
    targetId: String? = null,
    photoSources: List<String> = emptyList(),
    label: String,
    direct: suspend () -> Result<*>,
    onSent: suspend () -> Unit = {},
    onResult: (Boolean, String?) -> Unit,
) {
    when (val dispatch = submit(kind, payload, targetId, photoSources, label, direct)) {
        UploadQueueManager.Dispatch.Sent -> {
            onSent()
            onResult(true, null)
        }
        UploadQueueManager.Dispatch.Queued -> onResult(true, OFFLINE_SAVED_MESSAGE)
        is UploadQueueManager.Dispatch.Failed -> onResult(false, dispatch.message)
    }
}
