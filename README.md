# 🌿 TreSQL

TreSQL is a lightweight relational database engine built from scratch in Python,
designed as an academic project to explore how databases work under the hood.

Instead of hash tables or flat arrays, TreSQL uses self-balancing trees
as its core data structure — making primary key lookups fast and efficient by design.

## ✨ Features

- `CREATE TABLE` with custom columns
- `INSERT`, `SELECT`, `UPDATE`, `DELETE` operations
- Fast primary key search
- Linear scan search for any other attribute
- Data persistence — everything is saved to disk
- Two interfaces: interactive menu and SQL-like commands

## ⚠️ Scope & Limitations

TreSQL is intentionally minimal. It does not support:
- JOIN operations between tables
- Transactions or rollback
- Concurrent users
- Complex SQL (subqueries, GROUP BY, aggregations, etc.)

## 🎯 Purpose

This project was built to understand the internals of a relational database engine —
from storage and indexing to query parsing — using self-balancing trees as the
backbone of every table.

## 👥 Developers

- Luna A. Sandoval Rodríguez
- Nicolás Rodríguez Granados
- Geraldine A. Vargas Moreno
