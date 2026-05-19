package cli;

import manager.DatabaseManager;
import model.ObjectRecord;
import persistence.FileManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * DatabaseShell — Bucle REPL (Read-Eval-Print-Loop) del gestor NoSQL.
 *
 * Flujo por iteración:
 *   1. Lee una línea de stdin.
 *   2. Pasa la línea a {@link CommandParser#parse(String)}.
 *   3. Ejecuta la operación correspondiente en {@link DatabaseManager}.
 *   4. Imprime el resultado o mensaje de error.
 *   5. Repite hasta recibir EXIT o EOF (Ctrl+D / Ctrl+Z).
 *
 * Errores de parseo ({@link IllegalArgumentException}) y errores de
 * negocio ({@link IllegalStateException}) se capturan y muestran sin
 * abortar el REPL. Solo {@link IOException} no recuperable termina la sesión.
 *
 * Formato de salida:
 *   OK  : prefijo "OK: ..."
 *   ERROR: prefijo "ERROR: ..."
 *   LISTA: numerada, una línea por registro
 */
public class DatabaseShell {

    // ------------------------------------------------------------------ //
    //  CONSTANTES DE PRESENTACIÓN                                        //
    // ------------------------------------------------------------------ //

    private static final String PROMPT   = "db> ";
    private static final String DIVIDER  = "─".repeat(60);
    private static final String HELP =
            "Comandos disponibles:\n" +
            "  save {\"id\":\"<id>\",\"campo\":\"valor\",...}\n" +
            "  find <id>\n" +
            "  query <campo> <valor>\n" +
            "  querycontains <campo> <subcadena>\n" +
            "  update <id> {\"campo\":\"valor\",...}\n" +
            "  delete <id>\n" +
            "  list\n" +
            "  index\n" +
            "  help\n" +
            "  exit";

    // ------------------------------------------------------------------ //
    //  ESTADO                                                             //
    // ------------------------------------------------------------------ //

    private final DatabaseManager db;

    // ------------------------------------------------------------------ //
    //  CONSTRUCTORES                                                      //
    // ------------------------------------------------------------------ //

    /**
     * Crea un shell apuntando al archivo por defecto ({@code data/database.json}).
     *
     * @throws IOException si hay un error al inicializar el DatabaseManager.
     */
    public DatabaseShell() throws IOException {
        this.db = new DatabaseManager();
    }

    /**
     * Crea un shell con una ruta de archivo personalizada.
     * Útil para apuntar a un archivo de prueba.
     *
     * @param filePath ruta al archivo JSON de datos.
     * @throws IOException si hay un error al inicializar el DatabaseManager.
     */
    public DatabaseShell(String filePath) throws IOException {
        this.db = new DatabaseManager(new FileManager(filePath));
    }

    // ------------------------------------------------------------------ //
    //  BUCLE PRINCIPAL                                                    //
    // ------------------------------------------------------------------ //

    /**
     * Inicia el REPL. Bloquea hasta que el usuario escriba {@code exit}
     * o cierre stdin (EOF).
     */
    public void run() {
        printWelcome();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            while (true) {
                System.out.print(PROMPT);
                System.out.flush();

                line = reader.readLine();

                // EOF (Ctrl+D en Unix / Ctrl+Z en Windows)
                if (line == null) {
                    System.out.println("\n¡Hasta luego!");
                    break;
                }

                // Línea vacía: mostrar prompt de nuevo
                if (line.isBlank()) continue;

                // Manejar "help" antes de delegar al parser
                if (line.trim().equalsIgnoreCase("help")) {
                    System.out.println(HELP);
                    continue;
                }

                // Parsear y ejecutar
                boolean continueRepl = handleLine(line);
                if (!continueRepl) break;
            }
        } catch (IOException e) {
            System.err.println("ERROR FATAL de E/S: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ //
    //  DESPACHO DE COMANDOS                                               //
    // ------------------------------------------------------------------ //

    /**
     * Parsea una línea, ejecuta el comando y muestra el resultado.
     *
     * @return {@code false} si se debe salir del REPL; {@code true} en caso contrario.
     */
    private boolean handleLine(String line) {
        Command cmd;
        try {
            cmd = CommandParser.parse(line);
        } catch (IllegalArgumentException e) {
            printError("Sintaxis incorrecta: " + e.getMessage());
            return true;
        }

        try {
            return dispatch(cmd);
        } catch (IllegalArgumentException | IllegalStateException e) {
            printError(e.getMessage());
            return true;
        } catch (IOException e) {
            printError("Error de E/S: " + e.getMessage());
            return true;
        }
    }

    /**
     * Ejecuta la operación correspondiente al tipo del comando.
     *
     * @return {@code false} si el comando es EXIT; {@code true} en todos los demás casos.
     */
    private boolean dispatch(Command cmd) throws IOException {
        switch (cmd.getType()) {

            case SAVE -> {
                Map<String, String> params = cmd.getParams();
                String id = params.get("id");
                ObjectRecord record = new ObjectRecord(id);
                params.forEach((k, v) -> {
                    if (!k.equals("id")) record.setAttribute(k, v);
                });
                db.save(record);
                printOk("Registro '" + id + "' guardado.");
            }

            case FIND -> {
                String id = cmd.getParam("id");
                Optional<ObjectRecord> found = db.findById(id);
                if (found.isPresent()) {
                    printRecord(found.get());
                } else {
                    printError("No existe ningún registro con id='" + id + "'.");
                }
            }

            case QUERY -> {
                String field = cmd.getParam("field");
                String value = cmd.getParam("value");
                List<ObjectRecord> results = db.query(field, value);
                printList(results, "Resultados para " + field + "='" + value + "'");
            }

            case QUERY_CONTAINS -> {
                String field     = cmd.getParam("field");
                String substring = cmd.getParam("substring");
                List<ObjectRecord> results = db.queryContains(field, substring);
                printList(results, "Resultados para " + field + " contiene '" + substring + "'");
            }

            case UPDATE -> {
                String id = cmd.getParam("id");
                // Todos los params excepto "id" son los atributos a actualizar
                Map<String, String> attrs = new java.util.LinkedHashMap<>(cmd.getParams());
                attrs.remove("id");
                db.update(id, attrs);
                printOk("Registro '" + id + "' actualizado.");
            }

            case DELETE -> {
                String id = cmd.getParam("id");
                boolean deleted = db.delete(id);
                if (deleted) {
                    printOk("Registro '" + id + "' eliminado.");
                } else {
                    printError("No existe ningún registro con id='" + id + "'.");
                }
            }

            case LIST -> {
                List<ObjectRecord> all = db.listAll();
                printList(all, "Todos los registros (" + all.size() + ")");
            }

            case PRINT_INDEX -> {
                db.printIndex();
            }

            case EXIT -> {
                System.out.println("¡Hasta luego!");
                return false;
            }

            case UNKNOWN -> {
                String input = cmd.getParam("input");
                printError("Comando desconocido: '" + input + "'. Escribe 'help' para ver los comandos.");
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ //
    //  FORMATO DE SALIDA                                                  //
    // ------------------------------------------------------------------ //

    private void printWelcome() {
        System.out.println(DIVIDER);
        System.out.println("  Gestor BD NoSQL con Árbol B — ORDER=4");
        System.out.println("  Archivo: " + db.size() + " registro(s) cargado(s).");
        System.out.println("  Escribe 'help' para ver los comandos disponibles.");
        System.out.println(DIVIDER);
    }

    /** Imprime un mensaje de éxito. */
    private void printOk(String msg) {
        System.out.println("OK: " + msg);
    }

    /** Imprime un mensaje de error. */
    private void printError(String msg) {
        System.out.println("ERROR: " + msg);
    }

    /** Imprime un único registro con sus campos. */
    private void printRecord(ObjectRecord record) {
        System.out.println("  id: " + record.getId());
        record.toMap().forEach((k, v) -> System.out.println("  " + k + ": " + v));
    }

    /**
     * Imprime una lista numerada de registros.
     * Si está vacía muestra "Sin resultados."
     */
    private void printList(List<ObjectRecord> list, String header) {
        System.out.println(header + ":");
        if (list.isEmpty()) {
            System.out.println("  (sin resultados)");
            return;
        }
        int i = 1;
        for (ObjectRecord r : list) {
            System.out.println("  [" + i++ + "] " + r);
        }
    }
}
