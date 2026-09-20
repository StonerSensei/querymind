package dev.querymind.querymind.sql;

/**
 * The answer from the safety validator: is this SQL allowed to run?
 *
 * If it is not allowed, "reason" explains why (so we can show it to the user).
 */
public record ValidationResult(boolean allowed, String reason) {

    public static ValidationResult allow() {
        return new ValidationResult(true, null);
    }

    public static ValidationResult block(String reason) {
        return new ValidationResult(false, reason);
    }
}
