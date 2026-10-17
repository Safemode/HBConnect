package com.safemode.hbconnect.data.remote.models

/**
 * A whole-collection backup job on the server (GET/POST /api/v1/group/exports). The server
 * generates the archive asynchronously, so [status] moves through pending/running to completed or
 * failed, and the artifact is only downloadable once completed.
 */
data class ExportOut(
    val id: String? = null,
    val kind: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val status: String? = null,
    val progress: Int? = null,
    val sizeBytes: Long? = null,
    val error: String? = null,
)

/** GET /api/v1/group/exports wraps the list in { "items": … }. */
data class ExportListResult(val items: List<ExportOut>? = null)
