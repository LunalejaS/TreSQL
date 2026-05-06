"""
Layer 1 — Interface
Two interaction modes:
  1. SQL mode  — user types SQL statements at a prompt
  2. Menu mode — guided prompts for common operations
Both modes delegate to the SQL parser and query engine.
"""

import storage
from sql_parser import SQLParser
from query_engine import QueryEngine

_BANNER = r"""
 _____ _          ____   ___  _
|_   _| |_ ___   / ___| / _ \| |
  | | | '__/ _ \ \___ \| | | | |
  | | | || |_| | ___) | |_| | |___
  |_| |_| \___/ |____/ \__\_\_____|

  Lightweight Relational DB — powered by AVL trees
"""


class Interface:
    def __init__(self):
        self.parser = SQLParser()
        self.engine = QueryEngine()

    # ------------------------------------------------------------------
    # Public API
    # ------------------------------------------------------------------

    def execute_sql(self, sql):
        """Parse and execute an SQL string; return a human-readable string."""
        try:
            parsed = self.parser.parse(sql)
            return self._dispatch(parsed)
        except Exception as exc:
            return f"Error: {exc}"

    # ------------------------------------------------------------------
    # Internal dispatch
    # ------------------------------------------------------------------

    def _dispatch(self, parsed):
        t = parsed["type"]

        if t == "CREATE_TABLE":
            return self.engine.create_table(parsed["table"], parsed["columns"])

        if t == "INSERT":
            return self.engine.insert(parsed["table"], parsed["values"])

        if t == "SELECT":
            rows = self.engine.select(
                parsed["table"], parsed["columns"], parsed.get("conditions")
            )
            return self._format_rows(rows)

        if t == "UPDATE":
            return self.engine.update(
                parsed["table"], parsed["set_values"], parsed.get("conditions")
            )

        if t == "DELETE":
            return self.engine.delete(parsed["table"], parsed.get("conditions"))

        if t == "DROP_TABLE":
            return self.engine.drop_table(parsed["table"])

        if t == "SHOW_TABLES":
            tables = storage.list_tables()
            return "\n".join(tables) if tables else "No tables found."

        if t == "DESCRIBE":
            schema = self.engine.describe_table(parsed["table"])
            return self._format_schema(schema)

        return f"Unsupported command type: {t}"

    # ------------------------------------------------------------------
    # Formatters
    # ------------------------------------------------------------------

    def _format_rows(self, rows):
        if not rows:
            return "0 rows returned."
        cols = list(rows[0].keys())
        widths = {c: len(c) for c in cols}
        for row in rows:
            for c in cols:
                v = row.get(c)
                widths[c] = max(widths[c], len(str(v if v is not None else "")))
        sep = "-+-".join("-" * widths[c] for c in cols)
        header = " | ".join(c.ljust(widths[c]) for c in cols)
        lines = [header, sep]
        for row in rows:
            val_strs = []
            for c in cols:
                v = row.get(c)
                val_strs.append(str(v if v is not None else "").ljust(widths[c]))
            lines.append(" | ".join(val_strs))
        lines.append(f"\n{len(rows)} row(s) returned.")
        return "\n".join(lines)

    def _format_schema(self, schema):
        lines = [f"Table: {schema['table']}", ""]
        lines.append(f"{'Column':<20} {'Type':<15} {'PK':<4}")
        lines.append("-" * 42)
        for col in schema["columns"]:
            pk_mark = "YES" if col.get("primary_key") else ""
            lines.append(f"{col['name']:<20} {col['type']:<15} {pk_mark:<4}")
        return "\n".join(lines)

    # ------------------------------------------------------------------
    # SQL interactive mode
    # ------------------------------------------------------------------

    def run_sql_mode(self):
        """Read-eval-print loop for SQL statements."""
        print(_BANNER)
        print("Type SQL statements, 'menu' to switch to guided mode, or 'exit' to quit.")
        print("=" * 60)
        while True:
            try:
                line = input("TreSQL> ").strip()
            except (KeyboardInterrupt, EOFError):
                print("\nGoodbye!")
                break
            if not line:
                continue
            lower = line.lower()
            if lower in ("exit", "quit", "\\q"):
                print("Goodbye!")
                break
            if lower == "menu":
                self.run_menu()
                break
            print(self.execute_sql(line))

    # ------------------------------------------------------------------
    # Menu interactive mode
    # ------------------------------------------------------------------

    def run_menu(self):
        """Guided menu interface."""
        print("\n" + "=" * 50)
        print("  TreSQL — Guided Menu")
        print("=" * 50)
        menu_options = [
            ("Execute SQL statement",    self._menu_sql),
            ("Show all tables",          self._menu_show_tables),
            ("Describe a table",         self._menu_describe),
            ("Create a table",           self._menu_create),
            ("Insert a record",          self._menu_insert),
            ("Select records",           self._menu_select),
            ("Update records",           self._menu_update),
            ("Delete records",           self._menu_delete),
            ("Drop a table",             self._menu_drop),
            ("Exit",                     None),
        ]
        while True:
            print()
            for idx, (label, _) in enumerate(menu_options, 1):
                print(f"  {idx:2}. {label}")
            choice = input("\nChoose an option: ").strip()
            try:
                n = int(choice)
            except ValueError:
                print("Please enter a number.")
                continue
            if n < 1 or n > len(menu_options):
                print("Invalid option.")
                continue
            label, handler = menu_options[n - 1]
            if handler is None:
                print("Goodbye!")
                break
            try:
                handler()
            except (KeyboardInterrupt, EOFError):
                print("\nReturning to menu.")

    # ------------------------------------------------------------------
    # Menu handlers
    # ------------------------------------------------------------------

    def _menu_sql(self):
        sql = input("SQL> ").strip()
        print(self.execute_sql(sql))

    def _menu_show_tables(self):
        print(self.execute_sql("SHOW TABLES"))

    def _menu_describe(self):
        table = input("Table name: ").strip()
        print(self.execute_sql(f"DESCRIBE {table}"))

    def _menu_create(self):
        name = input("Table name: ").strip()
        print("Enter columns as 'name type [PRIMARY KEY]', one per line.")
        print("Type an empty line when done.")
        col_defs = []
        while True:
            col = input("  Column: ").strip()
            if not col:
                break
            col_defs.append(col)
        if not col_defs:
            print("No columns provided.")
            return
        sql = f"CREATE TABLE {name} ({', '.join(col_defs)})"
        print(self.execute_sql(sql))

    def _menu_insert(self):
        table = input("Table name: ").strip()
        try:
            schema = self.engine.describe_table(table)
        except Exception as exc:
            print(f"Error: {exc}")
            return
        cols, vals = [], []
        for col in schema["columns"]:
            raw = input(f"  {col['name']} ({col['type']}): ").strip()
            cols.append(col["name"])
            vals.append(f"'{raw}'" if not self._is_numeric(raw) else raw)
        sql = f"INSERT INTO {table} ({', '.join(cols)}) VALUES ({', '.join(vals)})"
        print(self.execute_sql(sql))

    def _menu_select(self):
        table = input("Table name: ").strip()
        where = input("WHERE clause (leave blank for all rows): ").strip()
        sql = f"SELECT * FROM {table}" + (f" WHERE {where}" if where else "")
        print(self.execute_sql(sql))

    def _menu_update(self):
        table = input("Table name: ").strip()
        set_clause = input("SET clause (e.g. name = 'Alice', grade = 4.0): ").strip()
        where = input("WHERE clause (leave blank to update all rows): ").strip()
        sql = f"UPDATE {table} SET {set_clause}" + (f" WHERE {where}" if where else "")
        print(self.execute_sql(sql))

    def _menu_delete(self):
        table = input("Table name: ").strip()
        where = input("WHERE clause (leave blank to delete all rows): ").strip()
        sql = f"DELETE FROM {table}" + (f" WHERE {where}" if where else "")
        print(self.execute_sql(sql))

    def _menu_drop(self):
        table = input("Table name: ").strip()
        confirm = input(f"Drop table '{table}'? This cannot be undone. [y/N]: ").strip().lower()
        if confirm == "y":
            print(self.execute_sql(f"DROP TABLE {table}"))
        else:
            print("Cancelled.")

    @staticmethod
    def _is_numeric(s):
        try:
            float(s)
            return True
        except ValueError:
            return False
