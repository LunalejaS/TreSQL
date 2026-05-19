import cli.DatabaseShell;
import java.io.IOException;

/**
 * Main — Punto de entrada del gestor de base de datos NoSQL con Árbol B.
 *
 * Uso:
 *   java Main                    → archivo por defecto: data/database.json
 *   java Main --file mi_bd.json  → archivo personalizado
 *
 * El programa inicia un REPL interactivo que termina con el comando
 * 'exit', 'quit' o EOF (Ctrl+D en Unix / Ctrl+Z en Windows).
 */
public class Main {

    public static void main(String[] args) {
        String filePath = parseArgs(args);

        try {
            DatabaseShell shell = (filePath == null)
                    ? new DatabaseShell()
                    : new DatabaseShell(filePath);
            shell.run();

        } catch (IOException e) {
            System.err.println("ERROR: No se pudo inicializar la base de datos.");
            System.err.println("Causa: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Extrae la ruta de archivo del argumento {@code --file <ruta>}.
     *
     * @return la ruta si se proporcionó, o {@code null} para usar el valor por defecto.
     */
    private static String parseArgs(String[] args) {
        if (args == null) return null;
        for (int i = 0; i < args.length - 1; i++) {
            if ("--file".equalsIgnoreCase(args[i])) {
                return args[i + 1];
            }
        }
        return null;
    }
}

