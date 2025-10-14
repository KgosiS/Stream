package com.functions.goshop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SignUpValidatorTest {

    @Test
    fun allFieldsRequired() {
        val result = SignUpValidator.validateInputs("", "user", "123456", "123456")
        assertEquals("All fields are required", result)
    }

    @Test
    fun passwordsDontMatch() {
        // FIX: Change the last password to a different value (e.g., "654321")
        val result = SignUpValidator.validateInputs("test@gmail.com", "user", "123456", "654321")
        assertEquals("Passwords do not match", result)
    }

    @Test
    fun invalidEmail() {
        val result = SignUpValidator.validateInputs("test", "user", "123456", "123456")
        assertEquals("Invalid email address", result)
    }

    @Test
    fun validInputs() {
        val result = SignUpValidator.validateInputs("test@gmail.com", "user", "123456", "123456")
        assertNull(result)
    }
}