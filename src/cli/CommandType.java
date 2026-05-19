package cli;

/**
 * CommandType — Enumera todos los comandos reconocidos por el REPL.
 *
 * Palabra clave que el usuario escribe (case-insensitive):
 *
 *   save           → SAVE
 *   find           → FIND
 *   query          → QUERY
 *   querycontains  → QUERY_CONTAINS
 *   update         → UPDATE
 *   delete         → DELETE
 *   list           → LIST
 *   index          → PRINT_INDEX
 *   exit / quit    → EXIT
 *   (cualquier otra) → UNKNOWN
 */
public enum CommandType {
    SAVE,
    FIND,
    QUERY,
    QUERY_CONTAINS,
    UPDATE,
    DELETE,
    LIST,
    PRINT_INDEX,
    EXIT,
    UNKNOWN
}
