"""
Layer 5 — Storage
Handles all file I/O.  Three file types per table:
  <name>.schema  — JSON column definitions
  <name>.dat     — JSON array of record dicts (None = deleted slot)
  <name>.idx     — JSON snapshot of the AVL tree
"""

import json
import os

# Override this in tests or configuration to change the data directory.
DATA_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "data")


# ------------------------------------------------------------------
# Internal path helpers
# ------------------------------------------------------------------

def _ensure_data_dir():
    os.makedirs(DATA_DIR, exist_ok=True)


def _schema_path(table_name):
    return os.path.join(DATA_DIR, f"{table_name}.schema")


def _data_path(table_name):
    return os.path.join(DATA_DIR, f"{table_name}.dat")


def _index_path(table_name):
    return os.path.join(DATA_DIR, f"{table_name}.idx")


# ------------------------------------------------------------------
# Schema (table definition)
# ------------------------------------------------------------------

def save_schema(table_name, schema):
    _ensure_data_dir()
    with open(_schema_path(table_name), "w") as f:
        json.dump(schema, f, indent=2)


def load_schema(table_name):
    path = _schema_path(table_name)
    if not os.path.exists(path):
        raise FileNotFoundError(f"Table '{table_name}' does not exist.")
    with open(path) as f:
        return json.load(f)


def table_exists(table_name):
    return os.path.exists(_schema_path(table_name))


def list_tables():
    _ensure_data_dir()
    return sorted(
        fname[:-7]
        for fname in os.listdir(DATA_DIR)
        if fname.endswith(".schema")
    )


# ------------------------------------------------------------------
# Records
# ------------------------------------------------------------------

def save_records(table_name, records):
    _ensure_data_dir()
    with open(_data_path(table_name), "w") as f:
        json.dump(records, f, indent=2)


def load_records(table_name):
    path = _data_path(table_name)
    if not os.path.exists(path):
        return []
    with open(path) as f:
        return json.load(f)


# ------------------------------------------------------------------
# Index (AVL tree snapshot)
# ------------------------------------------------------------------

def save_index(table_name, tree):
    _ensure_data_dir()
    with open(_index_path(table_name), "w") as f:
        json.dump(tree.to_dict(), f, indent=2)


def load_index(table_name, tree):
    path = _index_path(table_name)
    if not os.path.exists(path):
        return
    with open(path) as f:
        data = json.load(f)
    tree.from_dict(data)


# ------------------------------------------------------------------
# Table removal
# ------------------------------------------------------------------

def drop_table(table_name):
    for path in [_schema_path(table_name), _data_path(table_name), _index_path(table_name)]:
        if os.path.exists(path):
            os.remove(path)
