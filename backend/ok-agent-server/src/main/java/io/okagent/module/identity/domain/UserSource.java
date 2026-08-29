package io.okagent.module.identity.domain;

/**
 * Where an {@link User} principal originated.
 *
 * <ul>
 *   <li>{@link #CONSOLE}: created manually in the management console (has username/credentials).
 *   <li>{@link #CHANNEL}: auto-provisioned when a person first talks to a channel-bound bot.
 *       These are the one-user-id rows that multiple provider identities aggregate under.
 * </ul>
 */
public enum UserSource {
    CONSOLE,
    CHANNEL
}
