DROP TABLE IF EXISTS ai_message;
DROP TABLE IF EXISTS ai_conversation;
DROP TABLE IF EXISTS message;
DROP TABLE IF EXISTS announcement;
DROP TABLE IF EXISTS chat_message;
DROP TABLE IF EXISTS chat_room_participants;
DROP TABLE IF EXISTS online_status;
DROP TABLE IF EXISTS chat_room;
DROP TABLE IF EXISTS point_purchase_record;
DROP TABLE IF EXISTS recommend_point;
DROP TABLE IF EXISTS recommend_point_account;
DROP TABLE IF EXISTS publish_record;
DROP TABLE IF EXISTS favorite;
DROP TABLE IF EXISTS browse_history;
DROP TABLE IF EXISTS house;
DROP TABLE IF EXISTS area;
DROP TABLE IF EXISTS user_profile;
DROP TABLE IF EXISTS auth_user;

CREATE TABLE auth_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    password VARCHAR(128) NOT NULL,
    last_login TIMESTAMP NULL,
    is_superuser BOOLEAN NOT NULL DEFAULT FALSE,
    username VARCHAR(150) NOT NULL UNIQUE,
    first_name VARCHAR(150) NOT NULL DEFAULT '',
    last_name VARCHAR(150) NOT NULL DEFAULT '',
    email VARCHAR(254) NOT NULL DEFAULT '',
    is_staff BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    date_joined TIMESTAMP NOT NULL
);

CREATE TABLE user_profile (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    phone VARCHAR(20) NOT NULL UNIQUE,
    role VARCHAR(20) NOT NULL,
    avatar VARCHAR(500) NOT NULL DEFAULT '',
    create_time TIMESTAMP NOT NULL,
    update_time TIMESTAMP NOT NULL,
    user_id BIGINT NOT NULL UNIQUE,
    CONSTRAINT fk_profile_user FOREIGN KEY (user_id) REFERENCES auth_user(id)
);

CREATE TABLE area (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    parent_id BIGINT NULL,
    level INT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_area_parent FOREIGN KEY (parent_id) REFERENCES area(id)
);

CREATE TABLE house (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    price DECIMAL(10, 2) NOT NULL,
    area INT NOT NULL,
    rooms VARCHAR(50) NOT NULL,
    bedroom_count SMALLINT NOT NULL DEFAULT 0,
    living_room_count SMALLINT NOT NULL DEFAULT 0,
    bathroom_count SMALLINT NULL,
    kitchen_count SMALLINT NULL,
    house_type VARCHAR(20) NOT NULL,
    region_id BIGINT NULL,
    address_detail VARCHAR(300) NOT NULL DEFAULT '',
    image VARCHAR(500) NOT NULL DEFAULT '',
    landlord_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    click_count INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    create_time TIMESTAMP NOT NULL,
    update_time TIMESTAMP NOT NULL,
    CONSTRAINT fk_house_region FOREIGN KEY (region_id) REFERENCES area(id),
    CONSTRAINT fk_house_landlord FOREIGN KEY (landlord_id) REFERENCES user_profile(id)
);

CREATE TABLE favorite (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    create_time TIMESTAMP NOT NULL,
    house_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    CONSTRAINT uq_favorite_user_house UNIQUE (user_id, house_id),
    CONSTRAINT fk_favorite_house FOREIGN KEY (house_id) REFERENCES house(id) ON DELETE CASCADE,
    CONSTRAINT fk_favorite_user FOREIGN KEY (user_id) REFERENCES user_profile(id)
);

CREATE TABLE browse_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    create_time TIMESTAMP NOT NULL,
    house_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    CONSTRAINT uq_history_user_house UNIQUE (user_id, house_id),
    CONSTRAINT fk_history_house FOREIGN KEY (house_id) REFERENCES house(id) ON DELETE CASCADE,
    CONSTRAINT fk_history_user FOREIGN KEY (user_id) REFERENCES user_profile(id)
);

CREATE TABLE publish_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    amount DECIMAL(10, 2) NOT NULL,
    is_paid BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL,
    house_id BIGINT NULL,
    user_id BIGINT NOT NULL,
    CONSTRAINT fk_publish_house FOREIGN KEY (house_id) REFERENCES house(id) ON DELETE SET NULL,
    CONSTRAINT fk_publish_user FOREIGN KEY (user_id) REFERENCES user_profile(id)
);

CREATE TABLE recommend_point (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    points INT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    house_id BIGINT NOT NULL UNIQUE,
    CONSTRAINT fk_recommend_house FOREIGN KEY (house_id) REFERENCES house(id) ON DELETE CASCADE
);

CREATE TABLE point_purchase_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    points INT NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    house_id BIGINT NULL,
    user_id BIGINT NOT NULL,
    CONSTRAINT fk_purchase_house FOREIGN KEY (house_id) REFERENCES house(id) ON DELETE CASCADE,
    CONSTRAINT fk_purchase_user FOREIGN KEY (user_id) REFERENCES user_profile(id)
);

CREATE TABLE recommend_point_account (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    balance INT NOT NULL,
    total_purchased INT NOT NULL,
    total_invested INT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    user_id BIGINT NOT NULL UNIQUE,
    CONSTRAINT fk_account_user FOREIGN KEY (user_id) REFERENCES user_profile(id)
);

CREATE TABLE chat_room (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_type VARCHAR(10) NOT NULL,
    name VARCHAR(100) NOT NULL DEFAULT '',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    house_id BIGINT NULL,
    CONSTRAINT fk_chat_room_house FOREIGN KEY (house_id) REFERENCES house(id) ON DELETE SET NULL
);

CREATE TABLE chat_room_participants (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    chatroom_id BIGINT NOT NULL,
    userprofile_id BIGINT NOT NULL,
    CONSTRAINT uq_chat_participant UNIQUE (chatroom_id, userprofile_id),
    CONSTRAINT fk_chat_participant_room FOREIGN KEY (chatroom_id)
        REFERENCES chat_room(id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_participant_user FOREIGN KEY (userprofile_id)
        REFERENCES user_profile(id) ON DELETE CASCADE
);

CREATE TABLE chat_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    message_type VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    sender_id BIGINT NOT NULL,
    room_id BIGINT NOT NULL,
    is_recalled BOOLEAN NOT NULL DEFAULT FALSE,
    recall_reason VARCHAR(200) NOT NULL DEFAULT '',
    recalled_at TIMESTAMP NULL,
    CONSTRAINT fk_chat_message_room FOREIGN KEY (room_id)
        REFERENCES chat_room(id) ON DELETE CASCADE,
    CONSTRAINT fk_chat_message_sender FOREIGN KEY (sender_id)
        REFERENCES user_profile(id) ON DELETE CASCADE
);

CREATE TABLE online_status (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    is_online BOOLEAN NOT NULL DEFAULT FALSE,
    user_id BIGINT NOT NULL UNIQUE,
    last_seen TIMESTAMP NOT NULL,
    CONSTRAINT fk_online_user FOREIGN KEY (user_id)
        REFERENCES user_profile(id) ON DELETE CASCADE
);

CREATE TABLE message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    message_type VARCHAR(20) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    create_time TIMESTAMP NOT NULL,
    recipient_id BIGINT NOT NULL,
    related_house_id BIGINT NULL,
    sender_id BIGINT NULL,
    CONSTRAINT fk_message_recipient FOREIGN KEY (recipient_id)
        REFERENCES user_profile(id) ON DELETE CASCADE,
    CONSTRAINT fk_message_house FOREIGN KEY (related_house_id)
        REFERENCES house(id) ON DELETE SET NULL,
    CONSTRAINT fk_message_sender FOREIGN KEY (sender_id)
        REFERENCES user_profile(id) ON DELETE SET NULL
);

CREATE TABLE ai_conversation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL DEFAULT '新对话',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    user_id BIGINT NOT NULL,
    CONSTRAINT fk_ai_conversation_user FOREIGN KEY (user_id)
        REFERENCES user_profile(id) ON DELETE CASCADE
);

CREATE TABLE ai_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role VARCHAR(10) NOT NULL,
    content TEXT NOT NULL,
    metadata TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    conversation_id BIGINT NOT NULL,
    CONSTRAINT fk_ai_message_conversation FOREIGN KEY (conversation_id)
        REFERENCES ai_conversation(id) ON DELETE CASCADE
);

CREATE TABLE announcement (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    create_time TIMESTAMP NOT NULL,
    update_time TIMESTAMP NOT NULL,
    author_id BIGINT NOT NULL,
    CONSTRAINT fk_announcement_author FOREIGN KEY (author_id)
        REFERENCES user_profile(id) ON DELETE CASCADE
);
