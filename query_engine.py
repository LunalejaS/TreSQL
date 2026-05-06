"""
Layer 3 — Query Engine
Executes CREATE TABLE, INSERT, SELECT, UPDATE, and DELETE operations.
Uses the AVL index for fast primary-key look-ups and falls back to a
linear scan for any other attribute filter.
"""

import storage
from index_engine import AVLTree


class QueryEngine:
    def __init__(self):
        # In-memory cache: table_name -> AVLTree instance
        self._indexes = {}

    # ------------------------------------------------------------------
    # Internal helpers
    # ------------------------------------------------------------------

    def _get_index(self, table_name):
        if table_name not in self._indexes:
            tree = AVLTree()
            storage.load_index(table_name, tree)
            self._indexes[table_name] = tree
        return self._indexes[table_name]

    def _save_index(self, table_name):
        storage.save_index(table_name, self._indexes[table_name])

    def _primary_key_col(self, schema):
        for col in schema["columns"]:
            if col.get("primary_key"):
                return col["name"]
        return schema["columns"][0]["name"]

    def _col_names(self, schema):
        return [c["name"] for c in schema["columns"]]

    def _coerce(self, record_val, cond_val):
        """
        Try to cast *cond_val* to the type of *record_val* so that
        comparisons between parsed SQL values and stored JSON values
        work correctly even when types differ slightly.
        """
        if record_val is None:
            return cond_val
        try:
            if isinstance(record_val, bool):
                return bool(cond_val)
            if isinstance(record_val, int):
                return int(cond_val)
            if isinstance(record_val, float):
                return float(cond_val)
        except (ValueError, TypeError):
            pass
        return cond_val

    def _matches(self, record, conditions):
        """Return True when *record* satisfies every condition in the dict."""
        for col, val in conditions.items():
            rec_val = record.get(col)
            coerced = self._coerce(rec_val, val)
            if rec_val != coerced:
                return False
        return True

    def _project(self, record, columns):
        return {c: record.get(c) for c in columns}

    # ------------------------------------------------------------------
    # DDL
    # ------------------------------------------------------------------

    def create_table(self, table_name, columns):
        """
        Create a new table.

        *columns* is a list of dicts:
            {'name': str, 'type': str, 'primary_key': bool}
        The first column is auto-promoted to primary key when none is
        explicitly marked.
        """
        if storage.table_exists(table_name):
            raise ValueError(f"Table '{table_name}' already exists.")

        pk_count = sum(1 for c in columns if c.get("primary_key"))
        if pk_count == 0:
            columns[0]["primary_key"] = True
        elif pk_count > 1:
            raise ValueError("Only one primary key column is allowed.")

        schema = {"table": table_name, "columns": columns}
        storage.save_schema(table_name, schema)
        storage.save_records(table_name, [])
        self._indexes[table_name] = AVLTree()
        self._save_index(table_name)
        return f"Table '{table_name}' created."

    def drop_table(self, table_name):
        if not storage.table_exists(table_name):
            raise ValueError(f"Table '{table_name}' does not exist.")
        storage.drop_table(table_name)
        self._indexes.pop(table_name, None)
        return f"Table '{table_name}' dropped."

    def describe_table(self, table_name):
        return storage.load_schema(table_name)

    # ------------------------------------------------------------------
    # DML — INSERT
    # ------------------------------------------------------------------

    def insert(self, table_name, values):
        """
        Insert a record.  *values* is a dict {column_name: value}.
        Missing columns are stored as None.
        """
        schema = storage.load_schema(table_name)
        pk_col = self._primary_key_col(schema)
        col_names = self._col_names(schema)

        for key in values:
            if key not in col_names:
                raise ValueError(f"Unknown column '{key}' in table '{table_name}'.")

        record = {c["name"]: values.get(c["name"]) for c in schema["columns"]}

        pk_value = record.get(pk_col)
        if pk_value is None:
            raise ValueError(f"Primary key '{pk_col}' cannot be null.")

        tree = self._get_index(table_name)
        if tree.search(pk_value) is not None:
            raise ValueError(f"Duplicate primary key value: {pk_value!r}")

        records = storage.load_records(table_name)
        record_id = len(records)
        records.append(record)
        storage.save_records(table_name, records)

        tree.insert(pk_value, record_id)
        self._save_index(table_name)
        return "1 record inserted."

    # ------------------------------------------------------------------
    # DML — SELECT
    # ------------------------------------------------------------------

    def select(self, table_name, columns="*", conditions=None):
        """
        Query records.

        *columns*    — '*' or a list of column names to return.
        *conditions* — dict {col: value} (all ANDed) or None for all rows.

        When the primary key is part of *conditions* the AVL tree is used
        for O(log n) lookup; otherwise a linear scan is performed.
        """
        schema = storage.load_schema(table_name)
        col_names = self._col_names(schema)
        pk_col = self._primary_key_col(schema)

        if columns == "*":
            result_cols = col_names
        else:
            for c in columns:
                if c not in col_names:
                    raise ValueError(f"Unknown column '{c}' in table '{table_name}'.")
            result_cols = list(columns)

        # --- Fast path: primary-key point lookup via AVL tree ---
        if conditions and pk_col in conditions:
            tree = self._get_index(table_name)
            record_id = tree.search(conditions[pk_col])
            if record_id is None:
                return []
            records = storage.load_records(table_name)
            if record_id >= len(records) or records[record_id] is None:
                return []
            record = records[record_id]
            if self._matches(record, conditions):
                return [self._project(record, result_cols)]
            return []

        # --- Slow path: linear scan ---
        records = storage.load_records(table_name)
        results = []
        for record in records:
            if record is None:
                continue
            if conditions is None or self._matches(record, conditions):
                results.append(self._project(record, result_cols))
        return results

    # ------------------------------------------------------------------
    # DML — UPDATE
    # ------------------------------------------------------------------

    def update(self, table_name, set_values, conditions=None):
        """
        Update records that match *conditions*.
        *set_values* is a dict {col: new_value}.
        Updating the primary key column is not allowed.
        """
        schema = storage.load_schema(table_name)
        pk_col = self._primary_key_col(schema)
        col_names = self._col_names(schema)

        for key in set_values:
            if key not in col_names:
                raise ValueError(f"Unknown column '{key}' in table '{table_name}'.")
        if pk_col in set_values:
            raise ValueError("Updating the primary key column is not allowed.")

        records = storage.load_records(table_name)
        count = 0
        for i, record in enumerate(records):
            if record is None:
                continue
            if conditions is None or self._matches(record, conditions):
                records[i].update(set_values)
                count += 1

        storage.save_records(table_name, records)
        return f"{count} record(s) updated."

    # ------------------------------------------------------------------
    # DML — DELETE
    # ------------------------------------------------------------------

    def delete(self, table_name, conditions=None):
        """
        Delete records that match *conditions* (all rows when None).
        Deleted slots are set to None in the .dat file so that existing
        record_id values stored in the tree remain stable.
        """
        schema = storage.load_schema(table_name)
        pk_col = self._primary_key_col(schema)
        records = storage.load_records(table_name)
        tree = self._get_index(table_name)
        count = 0

        for i, record in enumerate(records):
            if record is None:
                continue
            if conditions is None or self._matches(record, conditions):
                tree.delete(record[pk_col])
                records[i] = None
                count += 1

        storage.save_records(table_name, records)
        self._save_index(table_name)
        return f"{count} record(s) deleted."
