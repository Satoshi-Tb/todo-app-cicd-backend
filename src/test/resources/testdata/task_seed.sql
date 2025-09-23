-- Seed data for TaskMapper tests
INSERT INTO tasks (title, description, status, due_date, version, created_at, updated_at)
VALUES ('Alpha task', 'First alpha description with foo', 'OPEN', DATE '2024-01-05', 0, TIMESTAMP '2024-01-01 10:00:00', TIMESTAMP '2024-01-01 10:00:00');

INSERT INTO tasks (title, description, status, due_date, version, created_at, updated_at)
VALUES ('Bravo task', 'Second bravo description', 'DOING', DATE '2024-01-06', 0, TIMESTAMP '2024-01-02 10:00:00', TIMESTAMP '2024-01-02 10:00:00');

INSERT INTO tasks (title, description, status, due_date, version, created_at, updated_at)
VALUES ('Foo bar', 'Contains foo keyword', 'DONE', DATE '2024-01-07', 0, TIMESTAMP '2024-01-03 10:00:00', TIMESTAMP '2024-01-03 10:00:00');

INSERT INTO tasks (title, description, status, due_date, version, created_at, updated_at)
VALUES ('Another foo', 'Also has foo in text', 'OPEN', DATE '2024-01-08', 0, TIMESTAMP '2024-01-04 10:00:00', TIMESTAMP '2024-01-04 10:00:00');
