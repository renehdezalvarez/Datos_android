package com.example.domain

/** Pure version comparison used by the self-update flow. */
object UpdateVersions {
    /** The installed app updates in place only when the published code is greater. */
    fun isNewer(publishedVersionCode: Int, installedVersionCode: Int): Boolean =
        publishedVersionCode > installedVersionCode
}
