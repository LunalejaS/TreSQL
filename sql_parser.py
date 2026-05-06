"""
Layer 2 — SQL Parser
Translates plain-text SQL into structured command dicts understood by the
Query Engine.

Supported statements:
  CREATE TABLE name (col type [PRIMARY KEY], ...)
  INSERT INTO name (col, ...) VALUES (val, ...)
  SELECT col, ... | * FROM name [WHERE col = val [AND ...]]
  UPDATE name SET col = val [, ...] [WHERE col = val [AND ...]]
  DELETE FROM name [WHERE col = val [AND ...]]
  DROP TABLE name
  SHOW TABLES
  DESCRIBE | DESC name
"""

import re


class SQLParser:
    """Parse a single SQL statement and return a command dict."""

    def parse(self, sql):
        sql = sql.strip().rstrip(";")
        if not sql:
            raise ValueError("Empty SQL statement.")

        upper = sql.upper()

        if upper.startswith("CREATE TABLE"):
            return self._parse_create_table(sql)
        if upper.startswith("INSERT INTO"):
            return self._parse_insert(sql)
        if upper.startswith("SELECT"):
            return self._parse_select(sql)
        if upper.startswith("UPDATE"):
            return self._parse_update(sql)
        if upper.startswith("DELETE FROM"):
            return self._parse_delete(sql)
        if upper.startswith("DROP TABLE"):
            return self._parse_drop_table(sql)
        if upper.startswith("SHOW TABLES"):
            return {"type": "SHOW_TABLES"}
        if upper.startswith("DESCRIBE") or upper.startswith("DESC "):
            parts = sql.split()
            if len(parts) < 2:
                raise ValueError("DESCRIBE requires a table name.")
            return {"type": "DESCRIBE", "table": parts[1]}

        raise ValueError(f"Unsupported SQL statement: '{sql[:60]}'")

    # ------------------------------------------------------------------
    # CREATE TABLE
    # ------------------------------------------------------------------

    def _parse_create_table(self, sql):
        pattern = r"CREATE\s+TABLE\s+(\w+)\s*\((.+)\)\s*$"
        m = re.match(pattern, sql, re.IGNORECASE | re.DOTALL)
        if not m:
            raise ValueError(f"Invalid CREATE TABLE syntax: {sql}")
        table_name = m.group(1)
        cols_str = m.group(2)
        columns = self._parse_column_defs(cols_str)
        return {"type": "CREATE_TABLE", "table": table_name, "columns": columns}

    def _parse_column_defs(self, cols_str):
        columns = []
        for col_def in cols_str.split(","):
            col_def = col_def.strip()
            if not col_def:
                continue
            parts = col_def.split()
            if len(parts) < 2:
                raise ValueError(f"Invalid column definition: '{col_def}'")
            name = parts[0]
            dtype = parts[1].upper()
            upper_parts = [p.upper() for p in parts]
            # Require both PRIMARY and KEY to appear (in any order) to mark a PK
            primary_key = "PRIMARY" in upper_parts and "KEY" in upper_parts
            columns.append({"name": name, "type": dtype, "primary_key": primary_key})
        if not columns:
            raise ValueError("CREATE TABLE requires at least one column.")
        return columns

    # ------------------------------------------------------------------
    # INSERT
    # ------------------------------------------------------------------

    def _parse_insert(self, sql):
        pattern = r"INSERT\s+INTO\s+(\w+)\s*\(([^)]+)\)\s+VALUES\s*\((.+)\)\s*$"
        m = re.match(pattern, sql, re.IGNORECASE | re.DOTALL)
        if not m:
            raise ValueError(f"Invalid INSERT syntax: {sql}")
        table_name = m.group(1)
        cols = [c.strip() for c in m.group(2).split(",")]
        vals = self._parse_value_list(m.group(3))
        if len(cols) != len(vals):
            raise ValueError(
                f"Column count ({len(cols)}) doesn't match value count ({len(vals)})."
            )
        return {"type": "INSERT", "table": table_name, "values": dict(zip(cols, vals))}

    def _parse_value_list(self, raw):
        """Split a comma-separated value list, respecting single-quoted strings."""
        values = []
        # Tokenise: quoted strings vs bare tokens, separated by commas
        token_re = re.compile(
            r"'[^']*'|\"[^\"]*\"|[^,]+"
        )
        # Split by comma while preserving quoted commas
        parts = re.split(r",(?=(?:[^'\"]*['\"][^'\"]*['\"])*[^'\"]*$)", raw)
        for part in parts:
            values.append(self._cast_value(part.strip()))
        return values

    def _cast_value(self, s):
        """Cast a SQL literal to the appropriate Python type."""
        if (s.startswith("'") and s.endswith("'")) or (
            s.startswith('"') and s.endswith('"')
        ):
            return s[1:-1]
        if s.upper() == "NULL":
            return None
        try:
            return int(s)
        except ValueError:
            pass
        try:
            return float(s)
        except ValueError:
            pass
        return s

    # ------------------------------------------------------------------
    # SELECT
    # ------------------------------------------------------------------

    def _parse_select(self, sql):
        pattern = r"SELECT\s+(.+?)\s+FROM\s+(\w+)(?:\s+WHERE\s+(.+))?\s*$"
        m = re.match(pattern, sql, re.IGNORECASE | re.DOTALL)
        if not m:
            raise ValueError(f"Invalid SELECT syntax: {sql}")
        cols_str = m.group(1).strip()
        table_name = m.group(2)
        where_str = m.group(3)

        columns = "*" if cols_str == "*" else [c.strip() for c in cols_str.split(",")]
        conditions = self._parse_where(where_str) if where_str else None
        return {
            "type": "SELECT",
            "table": table_name,
            "columns": columns,
            "conditions": conditions,
        }

    # ------------------------------------------------------------------
    # UPDATE
    # ------------------------------------------------------------------

    def _parse_update(self, sql):
        pattern = r"UPDATE\s+(\w+)\s+SET\s+(.+?)(?:\s+WHERE\s+(.+))?\s*$"
        m = re.match(pattern, sql, re.IGNORECASE | re.DOTALL)
        if not m:
            raise ValueError(f"Invalid UPDATE syntax: {sql}")
        table_name = m.group(1)
        set_str = m.group(2)
        where_str = m.group(3)
        set_values = self._parse_assignments(set_str)
        conditions = self._parse_where(where_str) if where_str else None
        return {
            "type": "UPDATE",
            "table": table_name,
            "set_values": set_values,
            "conditions": conditions,
        }

    # ------------------------------------------------------------------
    # DELETE
    # ------------------------------------------------------------------

    def _parse_delete(self, sql):
        pattern = r"DELETE\s+FROM\s+(\w+)(?:\s+WHERE\s+(.+))?\s*$"
        m = re.match(pattern, sql, re.IGNORECASE | re.DOTALL)
        if not m:
            raise ValueError(f"Invalid DELETE syntax: {sql}")
        table_name = m.group(1)
        where_str = m.group(2)
        conditions = self._parse_where(where_str) if where_str else None
        return {"type": "DELETE", "table": table_name, "conditions": conditions}

    # ------------------------------------------------------------------
    # DROP TABLE
    # ------------------------------------------------------------------

    def _parse_drop_table(self, sql):
        parts = sql.split()
        if len(parts) < 3:
            raise ValueError("Invalid DROP TABLE syntax: table name is required.")
        return {"type": "DROP_TABLE", "table": parts[2]}

    # ------------------------------------------------------------------
    # Shared clause parsers
    # ------------------------------------------------------------------

    def _parse_where(self, where_str):
        """
        Parse WHERE col = val [AND col = val ...].
        Returns a dict {col: value}.
        """
        conditions = {}
        parts = re.split(r"\s+AND\s+", where_str, flags=re.IGNORECASE)
        for part in parts:
            part = part.strip()
            m = re.match(r"(\w+)\s*=\s*(.+)", part)
            if not m:
                raise ValueError(f"Invalid WHERE condition: '{part}'")
            col = m.group(1)
            conditions[col] = self._cast_value(m.group(2).strip())
        return conditions

    def _parse_assignments(self, set_str):
        """
        Parse SET col = val [, col = val ...].
        Returns a dict {col: value}.
        """
        assignments = {}
        # Split by comma, but not inside quotes
        parts = re.split(r",(?=(?:[^'\"]*['\"][^'\"]*['\"])*[^'\"]*$)", set_str)
        for part in parts:
            part = part.strip()
            m = re.match(r"(\w+)\s*=\s*(.+)", part)
            if not m:
                raise ValueError(f"Invalid SET assignment: '{part}'")
            col = m.group(1)
            assignments[col] = self._cast_value(m.group(2).strip())
        return assignments
