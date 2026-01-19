package com.pioneer.messenger.data.security

import io.kotest.core.spec.style.StringSpec
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.forAll

/**
 * Property-based tests for PIN validation
 * 
 * **Feature: mkr-auth-rebranding, Property 4: PIN Format Validation**
 * **Validates: Requirements 2.2**
 */
class PinValidatorPropertyTest : StringSpec({
    
    // Generator for digit characters
    val digitCharArb = Arb.char('0'..'9')
    
    /**
     * Property 4: PIN Format Validation
     * *For any* PIN input, the validation SHALL accept it if and only if 
     * it contains exactly 4-6 digits (0-9).
     * 
     * **Feature: mkr-auth-rebranding, Property 4: PIN Format Validation**
     * **Validates: Requirements 2.2**
     */
    "PIN validation accepts only 4-6 digit strings" {
        forAll(Arb.string(0..20)) { input ->
            val isValid = PinValidator.isValidPin(input)
            val expectedValid = input.length in 4..6 && input.all { it.isDigit() }
            isValid == expectedValid
        }
    }
    
    "valid PINs (4-6 digits) are always accepted" {
        // Generator for valid PINs: strings of 4-6 digits
        val validPinArb = Arb.int(4..6).flatMap { length ->
            Arb.list(digitCharArb, length..length).map { it.joinToString("") }
        }
        
        forAll(validPinArb) { pin ->
            PinValidator.isValidPin(pin)
        }
    }
    
    "PINs shorter than 4 digits are always rejected" {
        val shortPinArb = Arb.int(0..3).flatMap { length ->
            if (length == 0) {
                Arb.constant("")
            } else {
                Arb.list(digitCharArb, length..length).map { it.joinToString("") }
            }
        }
        
        forAll(shortPinArb) { pin ->
            !PinValidator.isValidPin(pin)
        }
    }
    
    "PINs longer than 6 digits are always rejected" {
        val longPinArb = Arb.int(7..20).flatMap { length ->
            Arb.list(digitCharArb, length..length).map { it.joinToString("") }
        }
        
        forAll(longPinArb) { pin ->
            !PinValidator.isValidPin(pin)
        }
    }
    
    "PINs containing non-digit characters are always rejected" {
        // Generator for strings with at least one non-digit character
        val nonDigitCharArb = Arb.char('a'..'z')
        val mixedStringArb = Arb.int(4..6).flatMap { length ->
            // Create a string with at least one letter mixed with digits
            Arb.list(
                Arb.choice(digitCharArb, nonDigitCharArb),
                length..length
            ).filter { chars -> chars.any { !it.isDigit() } }
                .map { it.joinToString("") }
        }
        
        forAll(mixedStringArb) { pin ->
            !PinValidator.isValidPin(pin)
        }
    }
    
    /**
     * Property 5: PIN Confirmation Matching
     * *For any* pair of PIN strings (pin, confirmPin), the PIN setup SHALL succeed 
     * if and only if pin equals confirmPin.
     * 
     * **Feature: mkr-auth-rebranding, Property 5: PIN Confirmation Matching**
     * **Validates: Requirements 2.4, 2.5**
     */
    "PIN confirmation succeeds if and only if PINs match" {
        forAll(Arb.string(0..10), Arb.string(0..10)) { pin, confirmPin ->
            val result = PinValidator.pinsMatch(pin, confirmPin)
            val expected = pin == confirmPin
            result == expected
        }
    }
    
    "matching PINs always succeed confirmation" {
        // Generator for valid PINs: strings of 4-6 digits
        val validPinArb = Arb.int(4..6).flatMap { length ->
            Arb.list(digitCharArb, length..length).map { it.joinToString("") }
        }
        
        forAll(validPinArb) { pin ->
            // When the same PIN is used for both, confirmation should succeed
            PinValidator.pinsMatch(pin, pin)
        }
    }
    
    "different PINs always fail confirmation" {
        // Generator for pairs of different valid PINs
        val validPinArb = Arb.int(4..6).flatMap { length ->
            Arb.list(digitCharArb, length..length).map { it.joinToString("") }
        }
        
        forAll(validPinArb, validPinArb) { pin1, pin2 ->
            // Only test when PINs are actually different
            if (pin1 != pin2) {
                !PinValidator.pinsMatch(pin1, pin2)
            } else {
                true // Skip when they happen to be equal
            }
        }
    }
})
