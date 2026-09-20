CREATE TABLE admins (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    username   VARCHAR(50)  NOT NULL,
    password   VARCHAR(100) NOT NULL,
    created_at DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_admins_username (username)
) ENGINE = InnoDB;

-- 초기 관리자 계정: admin / admin (BCrypt)
INSERT INTO admins (username, password, created_at)
VALUES ('admin', '$2a$10$I3CRMhqrgzHMJl/zpzSz1.tmWbArTtoaK1CFjR0gp8Ngp0j8GYscS', NOW());
