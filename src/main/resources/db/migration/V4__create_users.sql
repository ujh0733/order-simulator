CREATE TABLE users (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    name       VARCHAR(50) NOT NULL,
    phone      VARCHAR(20) NOT NULL,
    email      VARCHAR(100) NULL,
    created_at DATETIME    NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_phone (phone),
    UNIQUE KEY uk_users_email (email)
) ENGINE = InnoDB;
