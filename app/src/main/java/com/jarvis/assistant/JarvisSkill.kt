package com.jarvis.assistant

import android.content.Context

/**
 * Declarative metadata for a built-in JARVIS capability.
 * Skills are compiled into the app; the registry never downloads or executes
 * arbitrary third-party code.
 */
enum class JarvisConfirmationPolicy {
    NEVER,
    BEFORE_EXECUTION,
    ALWAYS
}

data class JarvisSkillDescriptor(
    val id: String,
    val description: String,
    val requiredPermissions: Set<String> = emptySet(),
    val requiresNetwork: Boolean = false,
    val confirmationPolicy: JarvisConfirmationPolicy = JarvisConfirmationPolicy.NEVER
)

sealed class JarvisToolResult {
    data class Success(
        val message: String,
        val evidence: String? = null
    ) : JarvisToolResult()

    data class NeedsConfirmation(
        val prompt: String
    ) : JarvisToolResult()

    data class Failure(
        val reason: String
    ) : JarvisToolResult()

    fun toUserMessage(): String = when (this) {
        is Success -> message
        is NeedsConfirmation -> prompt
        is Failure -> reason
    }

    fun traceStatus(): String = when (this) {
        is Success -> "success"
        is NeedsConfirmation -> "needs_confirmation"
        is Failure -> "failure"
    }
}

/** One narrowly scoped capability available to the orchestrator. */
interface JarvisSkill {
    val descriptor: JarvisSkillDescriptor
    fun canHandle(command: String): Boolean
    fun execute(context: Context, command: String): JarvisToolResult
}

/**
 * Explicit allow-list of built-in skills.
 * Future capabilities must be registered here and declare their permissions
 * and confirmation policy before they can be called by the assistant.
 */
object JarvisSkillRegistry {
    private val skills: List<JarvisSkill> = listOf(
        ListingDraftSkill(),
        AppSearchSkill()
    )

    fun find(command: String): JarvisSkill? =
        skills.firstOrNull { skill -> runCatching { skill.canHandle(command) }.getOrDefault(false) }

    fun descriptors(): List<JarvisSkillDescriptor> = skills.map { it.descriptor }
}
