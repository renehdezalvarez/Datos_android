package com.example.domain

/** Pure version comparison used by the self-update flow. */
object UpdateVersions {
    /** The installed app updates in place only when the published code is greater. */
    fun isNewer(publishedVersionCode: Int, installedVersionCode: Int): Boolean =
        publishedVersionCode > installedVersionCode

    /**
     * A build is only offered when it can actually replace the installed one and
     * the user has not skipped it. Used by both the UI and the periodic worker so
     * a stale "Actualización disponible" never survives an install.
     */
    fun shouldOfferUpdate(
        publishedVersionCode: Int,
        installedVersionCode: Int,
        skippedVersionCode: Int
    ): Boolean = isNewer(publishedVersionCode, installedVersionCode) &&
        publishedVersionCode != skippedVersionCode
}
