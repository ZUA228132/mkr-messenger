package com.pioneer.messenger.data.security

/**
 * Callsign validation utility
 * Validates callsign format according to requirements:
 * - Must contain only lowercase letters (a-z, а-я), digits (0-9), and underscores (_)
 * - Must be at least 3 characters long
 * - Supports both Latin and Cyrillic characters
 * 
 * **Feature: mkr-auth-rebranding, Property 1: Callsign Character Validation**
 * **Validates: Requirements 1.2, 1.3**
 */
object CallsignValidator {
    
    /**
     * Validates that callsign contains only valid characters
     * Valid characters: lowercase letters (a-z, а-я), digits (0-9), underscores (_)
     * 
     * @param callsign The callsign string to validate
     * @return true if callsign contains only valid characters, false otherwise
     * 
     * **Feature: mkr-auth-rebranding, Property 1: Callsign Character Validation**
     * **Validates: Requirements 1.2**
     */
    fun hasValidCharacters(callsign: String): Boolean {
        if (callsign.isEmpty()) {
            return false
        }
        return callsign.all { char ->
            char.isLowerCase() || 
            char.isDigit() || 
            char == '_' ||
            char in 'а'..'я' ||  // Кириллица строчные
            char == 'ё'          // Дополнительно ё
        }
    }
    
    /**
     * Validates callsign length (minimum 3 characters)
     * 
     * @param callsign The callsign string to validate
     * @return true if callsign is at least 3 characters, false otherwise
     * 
     * **Validates: Requirements 1.3**
     */
    fun hasValidLength(callsign: String): Boolean {
        return callsign.length >= 3
    }
    
    /**
     * Full callsign validation - checks both characters and length
     * 
     * @param callsign The callsign string to validate
     * @return true if callsign is valid (correct characters and length), false otherwise
     */
    fun isValidCallsign(callsign: String): Boolean {
        return hasValidCharacters(callsign) && hasValidLength(callsign)
    }
}
