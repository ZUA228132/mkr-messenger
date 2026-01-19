package com.pioneer.messenger.data.security

import io.kotest.core.spec.style.StringSpec
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.forAll

/**
 * Property-based tests for Callsign validation
 * 
 * **Feature: mkr-auth-rebranding, Property 1: Callsign Character Validation**
 * **Validates: Requirements 1.2**
 */
class CallsignValidatorPropertyTest : StringSpec({
    
    // Generator for valid callsign characters: lowercase letters, digits, underscores
    val validCallsignCharArb = Arb.choice(
        Arb.char('a'..'z'),
        Arb.char('0'..'9'),
        Arb.constant('_')
    )
    
    // Generator for invalid characters (uppercase, special chars, etc.)
    val invalidCharArb = Arb.choice(
        Arb.char('A'..'Z'),  // uppercase letters
        Arb.char('!'..'/')   // special characters
    )
    
    /**
     * Property 1: Callsign Character Validation
     * *For any* string input as callsign, the validation function SHALL accept it 
     * if and only if it contains only lowercase letters (a-z), digits (0-9), and underscores (_).
     * 
     * **Feature: mkr-auth-rebranding, Property 1: Callsign Character Validation**
     * **Validates: Requirements 1.2**
     */
    "callsign character validation accepts only valid characters" {
        forAll(Arb.string(1..20)) { input ->
            val isValid = CallsignValidator.hasValidCharacters(input)
            val expectedValid = input.isNotEmpty() && input.all { char ->
                char.isLowerCase() || char.isDigit() || char == '_'
            }
            isValid == expectedValid
        }
    }
    
    "valid callsigns (lowercase, digits, underscores) are always accepted" {
        // Generator for valid callsigns: strings of valid characters
        val validCallsignArb = Arb.int(1..20).flatMap { length ->
            Arb.list(validCallsignCharArb, length..length).map { it.joinToString("") }
        }
        
        forAll(validCallsignArb) { callsign ->
            CallsignValidator.hasValidCharacters(callsign)
        }
    }
    
    "callsigns containing uppercase letters are always rejected" {
        // Generator for strings with at least one uppercase letter
        val uppercaseCallsignArb = Arb.int(1..10).flatMap { length ->
            Arb.list(
                Arb.choice(validCallsignCharArb, Arb.char('A'..'Z')),
                length..length
            ).filter { chars -> chars.any { it.isUpperCase() } }
                .map { it.joinToString("") }
        }
        
        forAll(uppercaseCallsignArb) { callsign ->
            !CallsignValidator.hasValidCharacters(callsign)
        }
    }
    
    "callsigns containing special characters are always rejected" {
        // Generator for strings with at least one special character
        val specialCharArb = Arb.choice(
            Arb.char('!'..'@'),  // !"#$%&'()*+,-./:;<=>?@
            Arb.char('['..'`'),  // [\]^_` (excluding underscore which is valid)
            Arb.char('{'..'~')   // {|}~
        ).filter { it != '_' }  // underscore is valid
        
        val specialCallsignArb = Arb.int(1..10).flatMap { length ->
            Arb.list(
                Arb.choice(validCallsignCharArb, specialCharArb),
                length..length
            ).filter { chars -> chars.any { char -> 
                !char.isLowerCase() && !char.isDigit() && char != '_' 
            } }
                .map { it.joinToString("") }
        }
        
        forAll(specialCallsignArb) { callsign ->
            !CallsignValidator.hasValidCharacters(callsign)
        }
    }
    
    "empty callsign is always rejected" {
        !CallsignValidator.hasValidCharacters("")
    }
})
