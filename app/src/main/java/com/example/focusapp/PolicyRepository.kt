package com.example.focusapp

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Repository for managing blocking policies and app-policy assignments.
 */
class PolicyRepository(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )
    
    // ==================== POLICIES ====================
    
    /**
     * Get all policies (system + user-created).
     */
    fun getAllPolicies(): List<BlockingPolicy> {
        val userPolicies = getUserPolicies()
        return BlockingPolicy.SYSTEM_POLICIES + userPolicies
    }
    
    /**
     * Get only user-created policies.
     */
    fun getUserPolicies(): List<BlockingPolicy> {
        val json = prefs.getString(KEY_USER_POLICIES, "[]") ?: "[]"
        return parsePoliciesFromJson(json)
    }
    
    /**
     * Get a policy by ID.
     */
    fun getPolicy(id: String): BlockingPolicy? {
        return getAllPolicies().find { it.id == id }
    }
    
    /**
     * Save a new or updated policy.
     */
    fun savePolicy(policy: BlockingPolicy) {
        if (policy.isSystemPolicy) return // Can't modify system policies
        
        val policies = getUserPolicies().toMutableList()
        val existingIndex = policies.indexOfFirst { it.id == policy.id }
        
        if (existingIndex >= 0) {
            policies[existingIndex] = policy
        } else {
            policies.add(policy)
        }
        
        prefs.edit().putString(KEY_USER_POLICIES, policiesToJson(policies)).apply()
    }
    
    /**
     * Delete a policy by ID.
     */
    fun deletePolicy(policyId: String) {
        val policies = getUserPolicies().filter { it.id != policyId }
        prefs.edit().putString(KEY_USER_POLICIES, policiesToJson(policies)).apply()
        
        // Also remove this policy from all app assignments
        val assignments = getAppPolicyAssignments().toMutableMap()
        assignments.forEach { (packageName, policyIds) ->
            assignments[packageName] = policyIds - policyId
        }
        saveAppPolicyAssignments(assignments)
    }
    
    // ==================== APP ASSIGNMENTS ====================
    
    /**
     * Get all app-policy assignments.
     */
    fun getAppPolicyAssignments(): Map<String, Set<String>> {
        val json = prefs.getString(KEY_APP_ASSIGNMENTS, "{}") ?: "{}"
        return parseAssignmentsFromJson(json)
    }
    
    /**
     * Get policies assigned to a specific app.
     */
    fun getPoliciesForApp(packageName: String): List<BlockingPolicy> {
        val policyIds = getAppPolicyAssignments()[packageName] ?: emptySet()
        return getAllPolicies().filter { it.id in policyIds }
    }
    
    /**
     * Assign a policy to an app.
     */
    fun assignPolicyToApp(packageName: String, policyId: String) {
        val assignments = getAppPolicyAssignments().toMutableMap()
        val current = assignments[packageName]?.toMutableSet() ?: mutableSetOf()
        current.add(policyId)
        assignments[packageName] = current
        saveAppPolicyAssignments(assignments)
    }
    
    /**
     * Remove a policy from an app.
     */
    fun removePolicyFromApp(packageName: String, policyId: String) {
        val assignments = getAppPolicyAssignments().toMutableMap()
        val current = assignments[packageName]?.toMutableSet() ?: return
        current.remove(policyId)
        if (current.isEmpty()) {
            assignments.remove(packageName)
        } else {
            assignments[packageName] = current
        }
        saveAppPolicyAssignments(assignments)
    }
    
    /**
     * Set all policies for an app (replaces existing).
     */
    fun setAppPolicies(packageName: String, policyIds: Set<String>) {
        val assignments = getAppPolicyAssignments().toMutableMap()
        if (policyIds.isEmpty()) {
            assignments.remove(packageName)
        } else {
            assignments[packageName] = policyIds
        }
        saveAppPolicyAssignments(assignments)
    }
    
    /**
     * Check if an app is currently blocked (any active policy).
     */
    fun isAppCurrentlyBlocked(packageName: String): Boolean {
        val policies = getPoliciesForApp(packageName)
        return policies.any { it.isActiveNow() }
    }
    
    // ==================== JSON SERIALIZATION ====================
    
    private fun saveAppPolicyAssignments(assignments: Map<String, Set<String>>) {
        val json = JSONObject()
        assignments.forEach { (packageName, policyIds) ->
            json.put(packageName, JSONArray(policyIds.toList()))
        }
        prefs.edit().putString(KEY_APP_ASSIGNMENTS, json.toString()).apply()
    }
    
    private fun parseAssignmentsFromJson(json: String): Map<String, Set<String>> {
        val result = mutableMapOf<String, Set<String>>()
        try {
            val obj = JSONObject(json)
            obj.keys().forEach { packageName ->
                val arr = obj.getJSONArray(packageName)
                val policyIds = mutableSetOf<String>()
                for (i in 0 until arr.length()) {
                    policyIds.add(arr.getString(i))
                }
                result[packageName] = policyIds
            }
        } catch (e: Exception) {
            // Return empty on parse error
        }
        return result
    }
    
    private fun policiesToJson(policies: List<BlockingPolicy>): String {
        val arr = JSONArray()
        policies.forEach { policy ->
            val obj = JSONObject()
            obj.put("id", policy.id)
            obj.put("name", policy.name)
            obj.put("startHour", policy.startTime.hour)
            obj.put("startMinute", policy.startTime.minute)
            obj.put("endHour", policy.endTime.hour)
            obj.put("endMinute", policy.endTime.minute)
            obj.put("days", JSONArray(policy.daysOfWeek.map { it.name }))
            arr.put(obj)
        }
        return arr.toString()
    }
    
    private fun parsePoliciesFromJson(json: String): List<BlockingPolicy> {
        val result = mutableListOf<BlockingPolicy>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val daysArr = obj.getJSONArray("days")
                val days = mutableSetOf<DayOfWeek>()
                for (j in 0 until daysArr.length()) {
                    days.add(DayOfWeek.valueOf(daysArr.getString(j)))
                }
                result.add(
                    BlockingPolicy(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        startTime = LocalTime.of(obj.getInt("startHour"), obj.getInt("startMinute")),
                        endTime = LocalTime.of(obj.getInt("endHour"), obj.getInt("endMinute")),
                        daysOfWeek = days,
                        isSystemPolicy = false
                    )
                )
            }
        } catch (e: Exception) {
            // Return empty on parse error
        }
        return result
    }
    
    companion object {
        private const val PREFS_NAME = "focus_app_policies"
        private const val KEY_USER_POLICIES = "user_policies"
        private const val KEY_APP_ASSIGNMENTS = "app_assignments"
    }
}
