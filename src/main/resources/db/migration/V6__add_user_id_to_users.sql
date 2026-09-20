-- users.id(내부 PK)와는 별개로, 외부에 노출 가능한 개인 식별자 컬럼을 추가한다.
ALTER TABLE users
    ADD COLUMN user_id VARCHAR(36) NOT NULL AFTER id,
    ADD UNIQUE KEY uk_users_user_id (user_id);
