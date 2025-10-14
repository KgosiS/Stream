package com.functions.goshop

// Standard email regex, independent of the Android framework
private val EMAIL_REGEX = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$".toRegex()

object SignUpValidator {

    fun validateInputs(
        email: String,
        name: String,
        password: String,
        confirmPassword: String
    ): String? {

        // 1. All fields required
        if (email.isBlank() || name.isBlank() || password.isBlank() || confirmPassword.isBlank()) {
            return "All fields are required"
        }

        // 2. Invalid email
        if (!email.matches(EMAIL_REGEX)) {
            return "Invalid email address"
        }

        // 3. Passwords Don't Match (Your previously failing logic)
        if (password != confirmPassword) {
            return "Passwords do not match"
        }

        // Success
        return null
    }
}