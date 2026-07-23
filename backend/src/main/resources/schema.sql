CREATE TABLE IF NOT EXISTS users (
    id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    password VARCHAR(255) NOT NULL,
    CONSTRAINT uq_users_username UNIQUE (username)
);

CREATE TABLE IF NOT EXISTS playlists (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    list_name   VARCHAR(200) NOT NULL,
    description VARCHAR(500) NOT NULL,
    CONSTRAINT uq_playlists_list_name UNIQUE (list_name)
);

CREATE TABLE IF NOT EXISTS songs (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    titulo      VARCHAR(200) NOT NULL,
    artista     VARCHAR(200) NOT NULL,
    album       VARCHAR(200) NOT NULL,
    anno        VARCHAR(20)  NOT NULL,
    genero      VARCHAR(100) NOT NULL,
    playlist_id BIGINT       NOT NULL,
    CONSTRAINT fk_songs_playlist FOREIGN KEY (playlist_id) REFERENCES playlists(id)
);
