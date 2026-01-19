package com.pioneer.messenger.data.security

/**
 * PIN validation utility
 * Validates PIN format according to requirements:
 * - Must be 4-6 digits
 * - Must contain only digits (0-9)
 */
object PinValidator {
    
    /**
     * Validates PIN format
     * @param pin The PIN string to validate
     * @return true if PIN is valid (4-6 digits only), false otherwise
     * 
     * **Feature: mkr-auth-rebranding, Property 4: PIN Format Validation**
     * **Validates: Requirements 2.2**
     */
    fun isValidPin(pin: String): Boolean {
        if (pin.length < 4 || pin.length > 6) {
            return false
        }
        return pin.all { it.isDigit() }
    }
    
    /**
     * Validates that two PINs match
     * @param pin The original PIN
     * @param confirmPin The confirmation PIN
     * @return true if both PINs are equal, false otherwise
     */
    fun pinsMatch(pin: String, confirmPin: String): Boolean {
        return pin == confirmPin
    }
}
