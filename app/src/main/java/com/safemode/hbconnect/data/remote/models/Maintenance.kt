package com.safemode.hbconnect.data.remote.models

/** A maintenance entry (GET /api/v1/entities/{id}/maintenance). Cost is a string in the API. */
data class MaintenanceEntry(
    val id: String? = null,
    val name: String? = null,
    val description: String? = null,
    val cost: String? = null,
    val scheduledDate: String? = null,
    val completedDate: String? = null,
    val itemID: String? = null,
    val itemName: String? = null,
) {
    /** Not yet completed (scheduled for the future or undated). */
    val isCompleted: Boolean get() = !completedDate.isNullOrBlank() && completedDate != EMPTY_DATE

    private companion object {
        // Homebox serializes an unset date as this sentinel rather than null/empty.
        const val EMPTY_DATE = "0001-01-01T00:00:00Z"
    }
}

/** Body for POST /api/v1/entities/{id}/maintenance and PUT /api/v1/maintenance/{id}. */
data class MaintenanceEntryRequest(
    val name: String,
    val description: String? = null,
    val cost: String? = null,
    val scheduledDate: String? = null,
    val completedDate: String? = null,
)
