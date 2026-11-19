package com.example

import com.example.domain.UpdateVersions
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateVersionsTest {

    @Test
    fun `published code greater than installed means update`() {
        assertTrue(UpdateVersions.isNewer(publishedVersionCode = 26, installedVersionCode = 25))
    }

    @Test
    fun `same or older code is not an update`() {
        assertFalse(UpdateVersions.isNewer(publishedVersionCode = 25, installedVersionCode = 25))
        assertFalse(UpdateVersions.isNewer(publishedVersionCode = 24, installedVersionCode = 25))
    }

    @Test
    fun `newer code is offered unless the user skipped it`() {
        assertTrue(
            UpdateVersions.shouldOfferUpdate(
                publishedVersionCode = 26,
                installedVersionCode = 25,
                skippedVersionCode = 0
            )
        )
        assertFalse(
            UpdateVersions.shouldOfferUpdate(
                publishedVersionCode = 26,
                installedVersionCode = 25,
                skippedVersionCode = 26
            )
        )
    }

    @Test
    fun `same installed code is never offered again`() {
        assertFalse(
            UpdateVersions.shouldOfferUpdate(
                publishedVersionCode = 28,
                installedVersionCode = 28,
                skippedVersionCode = 0
            )
        )
    }
}
