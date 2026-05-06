"""
TreSQL test suite.

Tests are grouped into four classes:
  TestAVLTree      — index_engine.py unit tests
  TestSQLParser    — sql_parser.py unit tests
  TestQueryEngine  — query_engine.py unit tests (file I/O)
  TestIntegration  — end-to-end via Interface.execute_sql()
"""

import os
import shutil
import sys

import tempfile

import pytest

# ---------------------------------------------------------------------------
# Redirect storage to a temporary directory so tests never touch production
# data and are fully isolated from each other.
# ---------------------------------------------------------------------------
_TEST_DATA_DIR = os.path.join(tempfile.gettempdir(), "tresql_test_data")

# Must be done before importing modules that read DATA_DIR at import time.
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

import storage  # noqa: E402  (needs sys.path tweak above)

storage.DATA_DIR = _TEST_DATA_DIR


@pytest.fixture(autouse=True)
def _clean_data():
    """Wipe the test data directory before and after every test."""
    if os.path.exists(_TEST_DATA_DIR):
        shutil.rmtree(_TEST_DATA_DIR)
    os.makedirs(_TEST_DATA_DIR, exist_ok=True)
    yield
    if os.path.exists(_TEST_DATA_DIR):
        shutil.rmtree(_TEST_DATA_DIR)


# ===========================================================================
# Layer 4 — AVL Tree
# ===========================================================================

class TestAVLTree:
    def setup_method(self):
        from index_engine import AVLTree
        self.AVLTree = AVLTree

    def _new(self):
        return self.AVLTree()

    def test_insert_and_search(self):
        t = self._new()
        t.insert(10, 0)
        t.insert(5, 1)
        t.insert(15, 2)
        assert t.search(10) == 0
        assert t.search(5) == 1
        assert t.search(15) == 2

    def test_search_missing_returns_none(self):
        t = self._new()
        t.insert(1, 0)
        assert t.search(99) is None

    def test_duplicate_key_updates_record_id(self):
        t = self._new()
        t.insert(1, 0)
        t.insert(1, 99)
        assert t.search(1) == 99

    def test_delete_leaf(self):
        t = self._new()
        t.insert(10, 0)
        t.insert(5, 1)
        t.delete(5)
        assert t.search(5) is None
        assert t.search(10) == 0

    def test_delete_node_with_two_children(self):
        t = self._new()
        for i in [10, 5, 15, 3, 7]:
            t.insert(i, i)
        t.delete(5)
        assert t.search(5) is None
        assert t.search(3) == 3
        assert t.search(7) == 7

    def test_delete_nonexistent_key_noop(self):
        t = self._new()
        t.insert(1, 0)
        t.delete(999)  # should not raise
        assert t.search(1) == 0

    def test_balanced_after_sequential_inserts(self):
        """Height must stay ≤ ⌈1.44 × log2(n+2)⌉ for any n."""
        import math
        t = self._new()
        n = 20
        for i in range(n):
            t.insert(i, i)
        max_height = math.ceil(1.44 * math.log2(n + 2))
        assert t.root.height <= max_height

    def test_inorder_sorted(self):
        t = self._new()
        keys = [8, 3, 10, 1, 6, 14]
        for k in keys:
            t.insert(k, k)
        result = [k for k, _ in t.inorder()]
        assert result == sorted(keys)

    def test_serialization_roundtrip(self):
        t = self._new()
        for k in [5, 3, 7, 1, 4]:
            t.insert(k, k * 10)
        data = t.to_dict()

        t2 = self._new()
        t2.from_dict(data)
        for k in [5, 3, 7, 1, 4]:
            assert t2.search(k) == k * 10

    def test_serialize_empty_tree(self):
        t = self._new()
        assert t.to_dict() is None
        t2 = self._new()
        t2.from_dict(None)
        assert t2.root is None

    def test_string_keys(self):
        t = self._new()
        t.insert("banana", 0)
        t.insert("apple", 1)
        t.insert("cherry", 2)
        assert t.search("apple") == 1
        assert t.search("cherry") == 2


# ===========================================================================
# Layer 2 — SQL Parser
# ===========================================================================

class TestSQLParser:
    def setup_method(self):
        from sql_parser import SQLParser
        self.parser = SQLParser()

    def p(self, sql):
        return self.parser.parse(sql)

    # CREATE TABLE
    def test_create_table_basic(self):
        r = self.p("CREATE TABLE students (id INT PRIMARY KEY, name VARCHAR)")
        assert r["type"] == "CREATE_TABLE"
        assert r["table"] == "students"
        assert r["columns"][0] == {"name": "id", "type": "INT", "primary_key": True}
        assert r["columns"][1] == {"name": "name", "type": "VARCHAR", "primary_key": False}

    def test_create_table_no_pk_flag(self):
        r = self.p("CREATE TABLE t (id INT, val TEXT)")
        assert r["columns"][0]["primary_key"] is False

    def test_create_table_trailing_semicolon(self):
        r = self.p("CREATE TABLE t (x INT);")
        assert r["table"] == "t"

    def test_create_table_invalid_raises(self):
        with pytest.raises(ValueError):
            self.p("CREATE TABLE")

    # INSERT
    def test_insert_int_and_string(self):
        r = self.p("INSERT INTO students (id, name) VALUES (1, 'Alice')")
        assert r["type"] == "INSERT"
        assert r["values"] == {"id": 1, "name": "Alice"}

    def test_insert_float(self):
        r = self.p("INSERT INTO grades (id, score) VALUES (1, 3.75)")
        assert r["values"]["score"] == pytest.approx(3.75)

    def test_insert_column_value_mismatch_raises(self):
        with pytest.raises(ValueError):
            self.p("INSERT INTO t (a, b) VALUES (1)")

    # SELECT
    def test_select_star(self):
        r = self.p("SELECT * FROM students")
        assert r["type"] == "SELECT"
        assert r["columns"] == "*"
        assert r["conditions"] is None

    def test_select_columns(self):
        r = self.p("SELECT id, name FROM students")
        assert r["columns"] == ["id", "name"]

    def test_select_where_int(self):
        r = self.p("SELECT * FROM students WHERE id = 1")
        assert r["conditions"] == {"id": 1}

    def test_select_where_string(self):
        r = self.p("SELECT * FROM students WHERE name = 'Bob'")
        assert r["conditions"] == {"name": "Bob"}

    def test_select_where_and(self):
        r = self.p("SELECT * FROM t WHERE a = 1 AND b = 2")
        assert r["conditions"] == {"a": 1, "b": 2}

    def test_select_invalid_raises(self):
        with pytest.raises(ValueError):
            self.p("SELECT FROM students")

    # UPDATE
    def test_update_with_where(self):
        r = self.p("UPDATE students SET name = 'Bob' WHERE id = 1")
        assert r["type"] == "UPDATE"
        assert r["set_values"] == {"name": "Bob"}
        assert r["conditions"] == {"id": 1}

    def test_update_without_where(self):
        r = self.p("UPDATE students SET active = 0")
        assert r["conditions"] is None

    # DELETE
    def test_delete_with_where(self):
        r = self.p("DELETE FROM students WHERE id = 5")
        assert r["type"] == "DELETE"
        assert r["conditions"] == {"id": 5}

    def test_delete_without_where(self):
        r = self.p("DELETE FROM students")
        assert r["conditions"] is None

    # DROP TABLE
    def test_drop_table(self):
        r = self.p("DROP TABLE students")
        assert r["type"] == "DROP_TABLE"
        assert r["table"] == "students"

    # SHOW TABLES
    def test_show_tables(self):
        r = self.p("SHOW TABLES")
        assert r["type"] == "SHOW_TABLES"

    # DESCRIBE
    def test_describe(self):
        r = self.p("DESCRIBE students")
        assert r["type"] == "DESCRIBE"
        assert r["table"] == "students"

    def test_desc_alias(self):
        r = self.p("DESC students")
        assert r["type"] == "DESCRIBE"

    # Unsupported
    def test_unsupported_raises(self):
        with pytest.raises(ValueError):
            self.p("GRANT ALL ON students TO user1")


# ===========================================================================
# Layer 3 — Query Engine
# ===========================================================================

class TestQueryEngine:
    def setup_method(self):
        from query_engine import QueryEngine
        self.engine = QueryEngine()

    def _students_schema(self):
        return [
            {"name": "id",   "type": "INT",     "primary_key": True},
            {"name": "name", "type": "VARCHAR",  "primary_key": False},
            {"name": "grade","type": "FLOAT",    "primary_key": False},
        ]

    def test_create_table(self):
        result = self.engine.create_table("students", self._students_schema())
        assert "created" in result.lower()
        assert storage.table_exists("students")

    def test_create_table_duplicate_raises(self):
        self.engine.create_table("t", [{"name": "id", "type": "INT", "primary_key": True}])
        with pytest.raises(ValueError, match="already exists"):
            self.engine.create_table("t", [{"name": "id", "type": "INT", "primary_key": True}])

    def test_create_auto_assigns_pk(self):
        self.engine.create_table("t", [
            {"name": "id", "type": "INT", "primary_key": False},
            {"name": "x",  "type": "INT", "primary_key": False},
        ])
        schema = self.engine.describe_table("t")
        assert schema["columns"][0]["primary_key"] is True

    def test_insert_and_select_all(self):
        self.engine.create_table("students", self._students_schema())
        self.engine.insert("students", {"id": 1, "name": "Alice", "grade": 3.8})
        self.engine.insert("students", {"id": 2, "name": "Bob",   "grade": 3.5})
        rows = self.engine.select("students")
        assert len(rows) == 2
        names = {r["name"] for r in rows}
        assert names == {"Alice", "Bob"}

    def test_select_by_primary_key_uses_index(self):
        self.engine.create_table("students", self._students_schema())
        self.engine.insert("students", {"id": 1, "name": "Alice", "grade": 3.8})
        self.engine.insert("students", {"id": 2, "name": "Bob",   "grade": 3.5})
        rows = self.engine.select("students", conditions={"id": 1})
        assert len(rows) == 1
        assert rows[0]["name"] == "Alice"

    def test_select_by_non_pk_linear_scan(self):
        self.engine.create_table("students", self._students_schema())
        self.engine.insert("students", {"id": 1, "name": "Alice", "grade": 3.8})
        self.engine.insert("students", {"id": 2, "name": "Alice", "grade": 3.5})
        self.engine.insert("students", {"id": 3, "name": "Bob",   "grade": 4.0})
        rows = self.engine.select("students", conditions={"name": "Alice"})
        assert len(rows) == 2

    def test_select_specific_columns(self):
        self.engine.create_table("students", self._students_schema())
        self.engine.insert("students", {"id": 1, "name": "Alice", "grade": 3.8})
        rows = self.engine.select("students", columns=["id", "name"])
        assert list(rows[0].keys()) == ["id", "name"]
        assert "grade" not in rows[0]

    def test_select_no_match_returns_empty(self):
        self.engine.create_table("students", self._students_schema())
        rows = self.engine.select("students", conditions={"id": 999})
        assert rows == []

    def test_insert_duplicate_pk_raises(self):
        self.engine.create_table("students", self._students_schema())
        self.engine.insert("students", {"id": 1, "name": "Alice", "grade": 3.8})
        with pytest.raises(ValueError, match="Duplicate"):
            self.engine.insert("students", {"id": 1, "name": "Bob", "grade": 3.0})

    def test_insert_null_pk_raises(self):
        self.engine.create_table("students", self._students_schema())
        with pytest.raises(ValueError, match="null"):
            self.engine.insert("students", {"name": "Alice", "grade": 3.8})

    def test_insert_unknown_column_raises(self):
        self.engine.create_table("students", self._students_schema())
        with pytest.raises(ValueError, match="Unknown column"):
            self.engine.insert("students", {"id": 1, "foo": "bar"})

    def test_update_with_condition(self):
        self.engine.create_table("students", self._students_schema())
        self.engine.insert("students", {"id": 1, "name": "Alice", "grade": 3.8})
        msg = self.engine.update("students", {"grade": 4.0}, {"id": 1})
        assert "1" in msg
        rows = self.engine.select("students", conditions={"id": 1})
        assert rows[0]["grade"] == 4.0

    def test_update_without_condition_updates_all(self):
        self.engine.create_table("students", self._students_schema())
        self.engine.insert("students", {"id": 1, "name": "Alice", "grade": 3.8})
        self.engine.insert("students", {"id": 2, "name": "Bob",   "grade": 3.5})
        msg = self.engine.update("students", {"grade": 0.0})
        assert "2" in msg

    def test_update_pk_raises(self):
        self.engine.create_table("students", self._students_schema())
        self.engine.insert("students", {"id": 1, "name": "Alice", "grade": 3.8})
        with pytest.raises(ValueError, match="primary key"):
            self.engine.update("students", {"id": 99}, {"id": 1})

    def test_delete_with_condition(self):
        self.engine.create_table("students", self._students_schema())
        self.engine.insert("students", {"id": 1, "name": "Alice", "grade": 3.8})
        self.engine.insert("students", {"id": 2, "name": "Bob",   "grade": 3.5})
        msg = self.engine.delete("students", {"id": 1})
        assert "1" in msg
        rows = self.engine.select("students")
        assert all(r["name"] != "Alice" for r in rows)

    def test_delete_removes_from_index(self):
        self.engine.create_table("students", self._students_schema())
        self.engine.insert("students", {"id": 1, "name": "Alice", "grade": 3.8})
        self.engine.delete("students", {"id": 1})
        rows = self.engine.select("students", conditions={"id": 1})
        assert rows == []

    def test_delete_all_rows(self):
        self.engine.create_table("students", self._students_schema())
        for i in range(5):
            self.engine.insert("students", {"id": i, "name": f"S{i}", "grade": 3.0})
        self.engine.delete("students")
        assert self.engine.select("students") == []

    def test_drop_table(self):
        self.engine.create_table("students", self._students_schema())
        msg = self.engine.drop_table("students")
        assert "dropped" in msg.lower()
        assert not storage.table_exists("students")

    def test_drop_nonexistent_raises(self):
        with pytest.raises(ValueError, match="does not exist"):
            self.engine.drop_table("ghost")

    def test_persistence_across_instances(self):
        """Data saved by one QueryEngine must be readable by another."""
        from query_engine import QueryEngine
        e1 = QueryEngine()
        e1.create_table("persist", [
            {"name": "k", "type": "INT", "primary_key": True},
            {"name": "v", "type": "TEXT", "primary_key": False},
        ])
        e1.insert("persist", {"k": 42, "v": "hello"})

        e2 = QueryEngine()
        rows = e2.select("persist", conditions={"k": 42})
        assert len(rows) == 1
        assert rows[0]["v"] == "hello"


# ===========================================================================
# End-to-end Integration — through Interface.execute_sql()
# ===========================================================================

class TestIntegration:
    def setup_method(self):
        from interface import Interface
        self.iface = Interface()

    def sql(self, stmt):
        return self.iface.execute_sql(stmt)

    def test_full_crud_workflow(self):
        # Create
        r = self.sql("CREATE TABLE students (id INT PRIMARY KEY, name VARCHAR, grade FLOAT)")
        assert "created" in r.lower()

        # Insert
        assert "1 record" in self.sql("INSERT INTO students (id, name, grade) VALUES (1, 'Alice', 3.8)")
        assert "1 record" in self.sql("INSERT INTO students (id, name, grade) VALUES (2, 'Bob', 3.5)")
        assert "1 record" in self.sql("INSERT INTO students (id, name, grade) VALUES (3, 'Carol', 4.0)")

        # Select all
        r = self.sql("SELECT * FROM students")
        assert "Alice" in r and "Bob" in r and "Carol" in r

        # Select with WHERE on PK
        r = self.sql("SELECT * FROM students WHERE id = 2")
        assert "Bob" in r
        assert "Alice" not in r

        # Select with WHERE on non-PK
        r = self.sql("SELECT name FROM students WHERE name = 'Alice'")
        assert "Alice" in r

        # Update
        r = self.sql("UPDATE students SET grade = 4.0 WHERE id = 1")
        assert "1" in r
        r = self.sql("SELECT grade FROM students WHERE id = 1")
        assert "4.0" in r

        # Delete
        r = self.sql("DELETE FROM students WHERE id = 2")
        assert "1" in r
        r = self.sql("SELECT * FROM students")
        assert "Bob" not in r
        assert "2 row" in r

        # Drop
        r = self.sql("DROP TABLE students")
        assert "dropped" in r.lower()

    def test_show_tables(self):
        self.sql("CREATE TABLE t1 (id INT PRIMARY KEY)")
        self.sql("CREATE TABLE t2 (id INT PRIMARY KEY)")
        r = self.sql("SHOW TABLES")
        assert "t1" in r
        assert "t2" in r

    def test_describe(self):
        self.sql("CREATE TABLE emp (emp_id INT PRIMARY KEY, dept VARCHAR)")
        r = self.sql("DESCRIBE emp")
        assert "emp_id" in r
        assert "YES" in r  # PK marker

    def test_error_unknown_table(self):
        r = self.sql("SELECT * FROM ghost")
        assert "error" in r.lower()

    def test_error_duplicate_pk(self):
        self.sql("CREATE TABLE t (id INT PRIMARY KEY)")
        self.sql("INSERT INTO t (id) VALUES (1)")
        r = self.sql("INSERT INTO t (id) VALUES (1)")
        assert "error" in r.lower()

    def test_select_zero_rows(self):
        self.sql("CREATE TABLE empty (id INT PRIMARY KEY)")
        r = self.sql("SELECT * FROM empty")
        assert "0 rows" in r

    def test_multicolumn_where(self):
        self.sql("CREATE TABLE t (id INT PRIMARY KEY, a INT, b INT)")
        self.sql("INSERT INTO t (id, a, b) VALUES (1, 10, 20)")
        self.sql("INSERT INTO t (id, a, b) VALUES (2, 10, 30)")
        r = self.sql("SELECT * FROM t WHERE a = 10 AND b = 20")
        assert "1" in r
        assert "30" not in r
