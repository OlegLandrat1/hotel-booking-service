package com.example.hotel.booking.user;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Учётные записи: регистрация, проверка пароля и администрирование пользователей.
 */
@Service
@Transactional
public class Accounts {

    private final UserRepository users;
    private final PasswordEncoder encoder;

    /**
     * @param users Пользователи
     * @param encoder Кодировщик паролей
     */
    public Accounts(final UserRepository users, final PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    /**
     * @param login Логин
     * @param password Пароль
     * @return Новый пользователь с ролью USER
     */
    public User register(final String login, final String password) {
        return this.create(login, password, Role.USER);
    }

    /**
     * @param login Логин
     * @param password Пароль
     * @param access Роль
     * @return Сохранённый пользователь
     */
    public User create(final String login, final String password, final Role access) {
        this.requireFree(login);
        return this.users.save(new User(login, this.encoder.encode(password), access));
    }

    /**
     * @param login Логин
     * @param password Пароль
     * @return Пользователь, если пароль верный
     */
    @Transactional(readOnly = true)
    public User authenticate(final String login, final String password) {
        return this.users.findByUsername(login)
                .filter(user -> this.encoder.matches(password, user.passwordHash()))
                .orElseThrow(() -> new BadCredentialsException("Login or password is wrong"));
    }

    /**
     * Меняет только переданные поля.
     * @param id Пользователь
     * @param login Новый логин
     * @param password Новый пароль
     * @param access Новая роль
     * @return Обновлённый пользователь
     */
    public User update(
            final Long id,
            final Optional<String> login,
            final Optional<String> password,
            final Optional<Role> access
    ) {
        final User user = this.find(id);
        login.filter(name -> !name.equals(user.username())).ifPresent(name -> {
            this.requireFree(name);
            user.rename(name);
        });
        password.map(this.encoder::encode).ifPresent(user::changePassword);
        access.ifPresent(user::changeRole);
        return user;
    }

    /**
     * Удаляет пользователя. Если у него есть бронирования, база откажет, и вызов вернёт 409.
     * @param id Пользователь
     */
    public void remove(final Long id) {
        this.users.delete(this.find(id));
        this.users.flush();
    }

    private User find(final Long id) {
        return this.users.findById(id).orElseThrow(
                () -> new java.util.NoSuchElementException(String.format("User %d was not found", id))
        );
    }

    private void requireFree(final String login) {
        if (this.users.findByUsername(login).isPresent()) {
            throw new IllegalStateException(String.format("Login %s is already taken", login));
        }
    }
}