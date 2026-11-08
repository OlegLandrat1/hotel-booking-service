package com.example.hotel.booking.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Пользователь системы. Пароль хранится только в виде хеша.
 */
@Entity
@Table(name = "users")
public class User {

    /**
     * Идентификатор, назначает база.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Логин, уникален в системе.
     */
    @Column(nullable = false, unique = true)
    private String username;

    /**
     * Хеш пароля (BCrypt).
     */
    @Column(nullable = false)
    private String password;

    /**
     * Роль пользователя.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    /**
     * Конструктор для JPA.
     */
    protected User() {
    }

    /**
     * Создаёт пользователя.
     * @param login Логин
     * @param passwordHash Хеш пароля
     * @param access Роль
     */
    public User(final String login, final String passwordHash, final Role access) {
        this.username = login;
        this.password = passwordHash;
        this.role = access;
    }

    /**
     * Меняет логин.
     * @param login Новый логин
     */
    public void rename(final String login) {
        this.username = login;
    }

    /**
     * Меняет пароль.
     * @param passwordHash Новый хеш
     */
    public void changePassword(final String passwordHash) {
        this.password = passwordHash;
    }

    /**
     * Меняет роль.
     * @param access Новая роль
     */
    public void changeRole(final Role access) {
        this.role = access;
    }

    /**
     * @return Идентификатор
     */
    public Long id() {
        return this.id;
    }

    /**
     * @return Логин
     */
    public String username() {
        return this.username;
    }

    /**
     * @return Хеш пароля
     */
    public String passwordHash() {
        return this.password;
    }

    /**
     * @return Роль
     */
    public Role role() {
        return this.role;
    }
}