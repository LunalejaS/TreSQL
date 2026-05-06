#!/usr/bin/env python3
"""
TreSQL — Entry point.

Usage:
  python main.py              # interactive SQL mode
  python main.py menu         # start in guided menu mode
  python main.py "SELECT * FROM students"   # one-shot SQL
"""

import sys
from interface import Interface


def main():
    iface = Interface()
    args = sys.argv[1:]

    if not args:
        iface.run_sql_mode()
    elif args[0].lower() == "menu":
        iface.run_menu()
    else:
        sql = " ".join(args)
        print(iface.execute_sql(sql))


if __name__ == "__main__":
    main()
