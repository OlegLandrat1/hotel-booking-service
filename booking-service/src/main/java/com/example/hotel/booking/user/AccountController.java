package com.example.hotel.booking.user;

import com.example.hotel.booking.security.JwtIssuer;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * Регистрация и вход открыты, администрирование пользователей только для ADMIN.
 * Subject токена — идентификатор пользователя.
 */
@RestController
@RequestMapping("/user")
public class AccountController {

    private final Accounts accounts;
    private final JwtIssuer tokens;

    /**
     * @param accounts Учётные записи
     * @param tokens Выпуск токенов
     */
    public AccountController(final Accounts accounts, final JwtIssuer tokens) {
        this.accounts = accounts;
        this.tokens = tokens;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Token register(@RequestBody final Credentials body) {
        return this.token(this.accounts.register(body.login(), body.password()));
    }

    @PostMapping("/auth")
    public Token auth(@RequestBody final Credentials body) {
        return this.token(this.accounts.authenticate(body.login(), body.password()));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public Item create(@RequestBody final Create body) {
        return new Item(
                this.accounts.create(
                        body.login(),
                        body.password(),
                        Optional.ofNullable(body.role()).orElse(Role.USER)
                )
        );
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Item update(@PathVariable("id") final Long id, @RequestBody final Patch body) {
        return new Item(
                this.accounts.update(
                        id,
                        Optional.ofNullable(body.login()),
                        Optional.ofNullable(body.password()),
                        Optional.ofNullable(body.role())
                )
        );
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable("id") final Long id) {
        this.accounts.remove(id);
    }

    private Token token(final User user) {
        return new Token(this.tokens.issueFor(String.valueOf(user.id()), user.role().name()));
    }

    /**
     * @param token Подписанный JWT
     */
    public record Token(String token) {
    }

    /**
     * @param login Логин
     * @param password Пароль
     */
    public record Credentials(String login, String password) {
    }

    /**
     * @param login Логин
     * @param password Пароль
     * @param role Роль, по умолчанию USER
     */
    public record Create(String login, String password, Role role) {
    }

    /**
     * @param login Новый логин, если меняется
     * @param password Новый пароль, если меняется
     * @param role Новая роль, если меняется
     */
    public record Patch(String login, String password, Role role) {
    }

    /**
     * Данные пользователя без пароля.
     * @param id Идентификатор
     * @param login Логин
     * @param role Роль
     */
    public record Item(Long id, String login, Role role) {

        /**
         * @param user Пользователь
         */
        public Item(final User user) {
            this(user.id(), user.username(), user.role());
        }
    }
}