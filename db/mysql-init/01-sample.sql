-- Sample data for the MySQL demo database.
-- Runs automatically the first time the MySQL container starts.
-- This is a "user database" the AI can query - not part of QueryMind itself.

CREATE TABLE products (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    price DECIMAL(10, 2) NOT NULL,
    in_stock BOOLEAN NOT NULL DEFAULT TRUE
);

INSERT INTO products (name, price, in_stock) VALUES
    ('Widget', 9.99, TRUE),
    ('Gadget', 19.95, TRUE),
    ('Gizmo', 4.50, FALSE),
    ('Sprocket', 12.00, TRUE);

-- Update table statistics so the approximate row count is accurate right away.
ANALYZE TABLE products;
