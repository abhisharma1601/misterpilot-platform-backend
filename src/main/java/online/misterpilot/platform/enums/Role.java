package online.misterpilot.platform.enums;

/**
 * User authorization role.
 *
 * Stored in the {@code users.role} column as its name (VARCHAR(20)),
 * defaulting to {@link #USER} for every new account.
 */
public enum Role {
    USER,
    ADMIN
}
