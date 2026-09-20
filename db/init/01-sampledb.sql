-- Creates a small sample database so we have something to query while testing.
-- This is demo data only. It is NOT part of QueryMind itself - it stands in
-- for "a user's database" that the AI agent connects to.
--
-- This script runs automatically the first time the Postgres container starts
-- with an empty data volume.

CREATE DATABASE sampledb;
\connect sampledb

CREATE TABLE customers (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    country VARCHAR(50),
    created_at TIMESTAMP DEFAULT now()
);

CREATE TABLE orders (
    id SERIAL PRIMARY KEY,
    customer_id INT NOT NULL REFERENCES customers(id),
    total NUMERIC(10, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP DEFAULT now()
);

INSERT INTO customers (name, email, country) VALUES
    ('Alice Johnson', 'alice@example.com', 'USA'),
    ('Bob Smith',     'bob@example.com',   'UK'),
    ('Carlos Diaz',   'carlos@example.com', 'Spain'),
    ('Diana Prince',  'diana@example.com', 'USA'),
    ('Ethan Hunt',    'ethan@example.com', 'Canada');

INSERT INTO orders (customer_id, total, status) VALUES
    (1, 250.00, 'PAID'),
    (1, 120.50, 'PAID'),
    (2,  90.00, 'PAID'),
    (3, 500.00, 'PAID'),
    (3,  75.25, 'PENDING'),
    (4, 310.00, 'PAID'),
    (5,  45.00, 'PAID'),
    (1, 200.00, 'PAID');

-- Update the table statistics so the approximate row counts are accurate
-- right away (used by the list_tables tool).
ANALYZE;
