package com.jarvis.assistant

import android.content.Context

/**
 * Compatibility entry point used by MainActivity.
 * Command execution is delegated to the registered-skill orchestrator.
 */
object JarvisAgent {
    fun execute(context: Context, raw: String): String? =
        JarvisOrchestrator.execute(context, raw)

    /** Call only after the user has explicitly approved the pending action. */
    fun executeConfirmed(context: Context, raw: String): String? =
        JarvisOrchestrator.execute(context, raw, confirmationGranted = true)
}
