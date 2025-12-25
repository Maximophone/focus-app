package com.example.focusapp

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

/**
 * Represents a reusable blocking policy with time constraints.
 */
data class BlockingPolicy(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val startTime: LocalTime,
    val endTime: LocalTime,
    val daysOfWeek: Set<DayOfWeek> = DayOfWeek.entries.toSet(), // Default: all days
    val isSystemPolicy: Boolean = false // True for hardcoded policies like "Always Block"
) {
    /**
     * Check if this policy is currently active.
     */
    fun isActiveNow(): Boolean = isActiveAt(LocalDateTime.now())
    
    /**
     * Check if this policy is active at a specific time.
     */
    fun isActiveAt(dateTime: LocalDateTime): Boolean {
        val currentDay = dateTime.dayOfWeek
        val currentTime = dateTime.toLocalTime()
        
        // Check if today is an active day
        if (currentDay !in daysOfWeek) return false
        
        // Handle time range
        return if (startTime <= endTime) {
            // Normal range (e.g., 07:00-10:00)
            currentTime >= startTime && currentTime < endTime
        } else {
            // Overnight range (e.g., 22:00-06:00)
            currentTime >= startTime || currentTime < endTime
        }
    }
    
    /**
     * Get a human-readable time range string.
     */
    fun getTimeRangeString(): String {
        val start = String.format("%02d:%02d", startTime.hour, startTime.minute)
        val end = String.format("%02d:%02d", endTime.hour, endTime.minute)
        return "$start - $end"
    }
    
    /**
     * Get a short days description.
     */
    fun getDaysString(): String {
        if (daysOfWeek.size == 7) return "Every day"
        if (daysOfWeek == setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)) return "Weekends"
        if (daysOfWeek == setOf(
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
            )) return "Weekdays"
        
        return daysOfWeek.joinToString(", ") { it.name.take(3).lowercase().replaceFirstChar { c -> c.uppercase() } }
    }
    
    companion object {
        // Hardcoded system policies
        val ALWAYS_BLOCK = BlockingPolicy(
            id = "system_always_block",
            name = "Always Block",
            startTime = LocalTime.of(0, 0),
            endTime = LocalTime.of(23, 59),
            daysOfWeek = DayOfWeek.entries.toSet(),
            isSystemPolicy = true
        )
        
        // More system policies can be added here
        val SYSTEM_POLICIES = listOf(ALWAYS_BLOCK)
    }
}
