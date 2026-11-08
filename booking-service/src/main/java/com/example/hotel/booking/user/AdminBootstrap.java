package com.example.hotel.booking.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Создаёт первого администратора из настроек, если его ещё нет.
 * Пароль берётся из переменной ADMIN_PASSWORD, без неё приложение не стартует.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(AdminBootstrap.class);

    private final Accounts accounts;
    private final UserRepository users;
    private final String login;
    private final String password;

    /**
     * @param accounts Учётные записи
     * @param users Пользователи
     * @param login Логин администратора
     * @param password Пароль администратора
     */
    public AdminBootstrap(
            final Accounts accounts,
            final UserRepository users,
            @Value("${app.bootstrap-admin.username}") final String login,
            @Value("${app.bootstrap-admin.password}") final String password
    ) {
        this.accounts = accounts;
        this.users = users;
        this.login = login;
        this.password = password;
    }

    @Override
    public void run(final ApplicationArguments args) {
        if (this.users.findByUsername(this.login).isEmpty()) {
            this.accounts.create(this.login, this.password, Role.ADMIN);
            LOG.info("bootstrap admin {} was created", this.login);
        }
    }
}