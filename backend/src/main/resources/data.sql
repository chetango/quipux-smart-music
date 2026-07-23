-- Usuario precargado: username=admin, password=admin123
-- Hash generado con BCrypt strength=10
INSERT INTO users (username, password)
SELECT 'admin', '$2a$10$7b8F/NT8ANLJPcA2HEvRcupbexVDw2828jmlrrh6xVsv0.6MesSRS'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'admin');
