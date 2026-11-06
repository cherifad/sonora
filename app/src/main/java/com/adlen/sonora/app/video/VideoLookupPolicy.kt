package com.adlen.sonora.app.video

/** Saver skips automatic app-resume lookup; a visible player or video intent still requests it. */
internal fun videoLookupOnResumeAllowed(
    allowAutomatic: Boolean,
    wantVideo: Boolean,
    videoOn: Boolean,
    playerVisible: Boolean,
): Boolean = allowAutomatic || wantVideo || videoOn || playerVisible
