package com.investguide.user;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.List;

/**
 * User account document (SPECIFICATION §6 {@code users}, ticket BE-A1).
 *
 * <p>Invariants enforced across the codebase:
 * <ul>
 *   <li>{@code email} is stored lowercased and unique (case-insensitive duplicates are rejected
 *       by the unique index because callers always lowercase before persist/lookup).</li>
 *   <li>{@code tokenBalance} starts at {@code 0} and is never negative (§7.7). It only becomes
 *       {@code 5} after the first email verification (§4.1, BE-A3). All balance mutations go
 *       through guarded conditional updates — never a naive read-modify-write.</li>
 *   <li>{@code passwordHash} is a BCrypt hash; the raw password is never stored or logged.</li>
 * </ul>
 *
 * <p>The unique index on {@code email} is declared here via {@link Indexed} and materialised at
 * startup ({@code spring.data.mongodb.auto-index-creation: true}); {@link UserIndexConfig} also
 * ensures it explicitly so the constraint exists even if auto-index creation is ever disabled.
 */
@Document(collection = "users")
public class User {

    @Id
    private String id;

    @Indexed(unique = true)
    @Field("email")
    private String email;

    @Field("passwordHash")
    private String passwordHash;

    @Field("emailVerified")
    private boolean emailVerified = false;

    @Field("tokenBalance")
    private int tokenBalance = 0;

    @Field("roles")
    private List<String> roles = List.of("USER");

    @CreatedDate
    @Field("createdAt")
    private Instant createdAt;

    @LastModifiedDate
    @Field("updatedAt")
    private Instant updatedAt;

    public User() {
    }

    /** Factory for a freshly registered, unverified account with a zero balance (BE-A2). */
    public static User newUnverified(String lowercasedEmail, String passwordHash) {
        User u = new User();
        u.email = lowercasedEmail;
        u.passwordHash = passwordHash;
        u.emailVerified = false;
        u.tokenBalance = 0;
        u.roles = List.of("USER");
        return u;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public int getTokenBalance() {
        return tokenBalance;
    }

    public void setTokenBalance(int tokenBalance) {
        this.tokenBalance = tokenBalance;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
