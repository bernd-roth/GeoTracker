package at.co.netconsulting.geotracker.viewmodel

import at.co.netconsulting.geotracker.domain.Event
import org.junit.Assert.assertEquals
import org.junit.Test

class UploadEventsViewModelTest {
    @Test
    fun `replacement targets only selected events matched on server`() {
        val matched = event(id = 1, sessionId = "local-session")
        val missing = event(id = 2, sessionId = null)
        val unselected = event(id = 3, sessionId = "other-local-session")

        val targets = UploadEventsViewModel.replacementTargets(
            events = listOf(matched, missing, unselected),
            selectedEventIds = setOf(1, 2),
            uploadProgress = mapOf(
                1 to UploadEventsViewModel.UploadState.AlreadyExists("remote-session"),
                2 to UploadEventsViewModel.UploadState.NeedsUpload,
                3 to UploadEventsViewModel.UploadState.AlreadyExists("other-remote-session")
            )
        )

        assertEquals(listOf(matched to "remote-session"), targets)
    }

    private fun event(id: Int, sessionId: String?) = Event(
        eventId = id,
        userId = 1,
        eventName = "Test $id",
        eventDate = "2026-08-27",
        artOfSport = "Running",
        comment = "",
        sessionId = sessionId,
        isUploaded = sessionId != null
    )
}
