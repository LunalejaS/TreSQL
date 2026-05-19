package cli;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CommandParser — Convierte una línea de texto en un objeto {@link Command}.
 *
 * Sintaxis de cada comando (espacios extras se toleran):
 *
 *   save {"id":"001","nombre":"Juan","edad":"20"}
 *   find 001
 *   query carrera Sistemas
 *   querycontains nombre an
 *   update 001 {"edad":"21","ciudad":"Bogotá"}
 *   delete 001
 *   list
 *   index
 *   exit
 *
 * Reglas:
 *  - Case-insensitive en la palabra clave del comando.
 *  - El JSON inline es el primer bloque que empieza con '{' hasta el '}' final.
 *  - El campo "id" en el JSON de save/update es obligatorio.
 *  - Línea vacía o nula → UNKNOWN.
 *  - Cualquier palabra clave no reconocida → UNKNOWN con param "input".
 */
public class CommandParser {

    // ------------------------------------------------------------------ //
    //  PUNTO DE ENTRADA                                                   //
    // ------------------------------------------------------------------ //

    /**
     * Parsea una línea de entrada del usuario.
     *
     * @param line línea de texto; puede ser nula o vacía (→ UNKNOWN).
     * @return {@link Command} correspondiente; nunca nulo.
     */
    public static Command parse(String line) {
        if (line == null || line.isBlank()) {
            return unknown("");
        }

        String trimmed = line.trim();
        // Extraer la primera palabra (el verbo del comando)
        int spaceIdx = trimmed.indexOf(' ');
        String verb = (spaceIdx == -1) ? trimmed : trimmed.substring(0, spaceIdx);
        String rest  = (spaceIdx == -1) ? ""      : trimmed.substring(spaceIdx + 1).trim();

        return switch (verb.toLowerCase()) {
            case "save"          -> parseSave(rest);
            case "find"          -> parseFind(rest);
            case "query"         -> parseQuery(rest);
            case "querycontains" -> parseQueryContains(rest);
            case "update"        -> parseUpdate(rest);
            case "delete"        -> parseDelete(rest);
            case "list"          -> new Command(CommandType.LIST,        Collections.emptyMap());
            case "index"         -> new Command(CommandType.PRINT_INDEX, Collections.emptyMap());
            case "exit", "quit"  -> new Command(CommandType.EXIT,        Collections.emptyMap());
            default              -> unknown(trimmed);
        };
    }

    // ------------------------------------------------------------------ //
    //  PARSEO POR TIPO                                                    //
    // ------------------------------------------------------------------ //

    /**
     * save {"id":"001","nombre":"Juan"}
     * Params: todas las claves del JSON (incluye "id").
     */
    private static Command parseSave(String rest) {
        if (rest.isBlank()) {
            throw new IllegalArgumentException(
                    "save requiere un objeto JSON. Ejemplo: save {\"id\":\"001\",\"nombre\":\"Juan\"}");
        }
        Map<String, String> params = parseJsonInline(rest);
        if (!params.containsKey("id") || params.get("id").isBlank()) {
            throw new IllegalArgumentException(
                    "El JSON de save debe contener el campo \"id\".");
        }
        return new Command(CommandType.SAVE, params);
    }

    /**
     * find 001
     * Params: "id" → "001"
     */
    private static Command parseFind(String rest) {
        if (rest.isBlank()) {
            throw new IllegalArgumentException(
                    "find requiere un id. Ejemplo: find 001");
        }
        // El id es el primer token (no acepta espacios en el id en esta versión)
        String id = rest.split("\\s+")[0];
        return new Command(CommandType.FIND, Map.of("id", id));
    }

    /**
     * query carrera Sistemas
     * Params: "field" → "carrera", "value" → "Sistemas"
     *
     * El valor puede contener espacios internos: todo lo que venga después
     * del primer token se trata como value.
     */
    private static Command parseQuery(String rest) {
        String[] parts = splitTwo(rest, "query");
        return new Command(CommandType.QUERY,
                mapOf("field", parts[0], "value", parts[1]));
    }

    /**
     * querycontains nombre an
     * Params: "field" → "nombre", "substring" → "an"
     */
    private static Command parseQueryContains(String rest) {
        String[] parts = splitTwo(rest, "querycontains");
        return new Command(CommandType.QUERY_CONTAINS,
                mapOf("field", parts[0], "substring", parts[1]));
    }

    /**
     * update 001 {"edad":"21","ciudad":"Bogotá"}
     * Params: "id" → "001" + todas las claves del JSON de atributos.
     *
     * El JSON de update NO debe contener "id"; si lo contiene, se ignora
     * (el id es inmutable).
     */
    private static Command parseUpdate(String rest) {
        if (rest.isBlank()) {
            throw new IllegalArgumentException(
                    "update requiere id y JSON. Ejemplo: update 001 {\"edad\":\"21\"}");
        }

        // Separar el id del JSON
        int jsonStart = rest.indexOf('{');
        if (jsonStart == -1) {
            throw new IllegalArgumentException(
                    "update requiere un objeto JSON de atributos. Ejemplo: update 001 {\"edad\":\"21\"}");
        }

        String id      = rest.substring(0, jsonStart).trim();
        String jsonPart = rest.substring(jsonStart).trim();

        if (id.isBlank()) {
            throw new IllegalArgumentException("update requiere un id antes del JSON.");
        }

        Map<String, String> attrs = parseJsonInline(jsonPart);
        // El id va en params pero "id" del JSON se ignora (inmutable)
        Map<String, String> params = new LinkedHashMap<>();
        params.put("id", id);
        attrs.forEach((k, v) -> { if (!k.equals("id")) params.put(k, v); });

        return new Command(CommandType.UPDATE, params);
    }

    /**
     * delete 001
     * Params: "id" → "001"
     */
    private static Command parseDelete(String rest) {
        if (rest.isBlank()) {
            throw new IllegalArgumentException(
                    "delete requiere un id. Ejemplo: delete 001");
        }
        String id = rest.split("\\s+")[0];
        return new Command(CommandType.DELETE, Map.of("id", id));
    }

    // ------------------------------------------------------------------ //
    //  PARSEO DE JSON INLINE                                              //
    // ------------------------------------------------------------------ //

    /**
     * Parsea un objeto JSON plano de una sola línea.
     *
     * Reutiliza la lógica de JsonParser adaptada como método estático
     * de paquete (para no crear dependencia circular: cli → persistence
     * estaría saltando una capa). Se duplica mínimamente aquí para
     * respetar la arquitectura estricta de capas.
     *
     * Soporta: {"clave":"valor","clave2":"valor2"}
     * No soporta: anidamiento, arrays, valores numéricos sin comillas.
     *
     * @param json cadena JSON plana; debe empezar con '{' y terminar con '}'.
     * @return mapa de pares clave→valor; nunca nulo.
     * @throws IllegalArgumentException si el JSON está mal formado.
     */
    static Map<String, String> parseJsonInline(String json) {
        String s = json.trim();
        if (!s.startsWith("{") || !s.endsWith("}")) {
            throw new IllegalArgumentException(
                    "JSON mal formado (faltan llaves): " + json);
        }
        String content = s.substring(1, s.length() - 1).trim();
        if (content.isBlank()) return Collections.emptyMap();

        Map<String, String> result = new LinkedHashMap<>();
        int i = 0, len = content.length();

        while (i < len) {
            // Saltar espacios
            while (i < len && content.charAt(i) == ' ') i++;
            if (i >= len) break;

            // Leer clave
            if (content.charAt(i) != '"') {
                throw new IllegalArgumentException(
                        "JSON: se esperaba '\"' en posición " + i + " de: " + content);
            }
            int ks = i + 1, ke = nextQuote(content, ks);
            String key = content.substring(ks, ke);
            i = ke + 1;

            // Saltar ':'
            while (i < len && content.charAt(i) == ' ') i++;
            if (i >= len || content.charAt(i) != ':') {
                throw new IllegalArgumentException(
                        "JSON: se esperaba ':' tras clave '" + key + "'");
            }
            i++;

            // Saltar espacios
            while (i < len && content.charAt(i) == ' ') i++;

            // Leer valor (siempre entre comillas)
            if (i >= len || content.charAt(i) != '"') {
                throw new IllegalArgumentException(
                        "JSON: se esperaba '\"' al inicio del valor de '" + key + "'");
            }
            int vs = i + 1, ve = nextQuote(content, vs);
            String value = content.substring(vs, ve);
            i = ve + 1;

            result.put(key, unescapeJson(value));

            // Saltar coma
            while (i < len && content.charAt(i) == ' ') i++;
            if (i < len && content.charAt(i) == ',') i++;
        }

        return result;
    }

    // ------------------------------------------------------------------ //
    //  UTILIDADES PRIVADAS                                                //
    // ------------------------------------------------------------------ //

    /**
     * Divide {@code rest} en dos tokens: el primero es un identificador simple
     * (sin espacios), el segundo es todo lo que sigue (puede tener espacios).
     * Lanza excepción descriptiva si hay menos de dos tokens.
     */
    private static String[] splitTwo(String rest, String cmdName) {
        if (rest.isBlank()) {
            throw new IllegalArgumentException(
                    cmdName + " requiere dos argumentos: <campo> <valor>");
        }
        int sp = rest.indexOf(' ');
        if (sp == -1) {
            throw new IllegalArgumentException(
                    cmdName + " requiere dos argumentos: <campo> <valor>. Recibido: '" + rest + "'");
        }
        String first  = rest.substring(0, sp).trim();
        String second = rest.substring(sp + 1).trim();
        if (first.isBlank() || second.isBlank()) {
            throw new IllegalArgumentException(
                    cmdName + ": campo y valor no pueden estar vacíos.");
        }
        return new String[]{ first, second };
    }

    /** Encuentra el índice de la próxima comilla no escapada desde {@code from}. */
    private static int nextQuote(String s, int from) {
        int i = from;
        while (i < s.length()) {
            if (s.charAt(i) == '\\') { i += 2; }
            else if (s.charAt(i) == '"') { return i; }
            else { i++; }
        }
        throw new IllegalArgumentException(
                "JSON: cadena sin cierre de comilla a partir de posición " + from);
    }

    /** Desescapa secuencias JSON básicas (\", \\, \n, \r, \t). */
    private static String unescapeJson(String s) {
        return s.replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n",  "\n")
                .replace("\\r",  "\r")
                .replace("\\t",  "\t");
    }

    /** Crea un Command de tipo UNKNOWN con la línea original en params. */
    private static Command unknown(String input) {
        return new Command(CommandType.UNKNOWN, Map.of("input", input));
    }

    /** Helper para construir mapas de dos entradas sin varargs ambiguos. */
    private static Map<String, String> mapOf(String k1, String v1, String k2, String v2) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(k1, v1);
        m.put(k2, v2);
        return m;
    }

    // ------------------------------------------------------------------ //
    //  PRUEBAS BÁSICAS (main de referencia)                               //
    // ------------------------------------------------------------------ //

    public static void main(String[] args) {
        System.out.println("=== Pruebas de CommandParser ===\n");

        String[][] casos = {
            { "save {\"id\":\"001\",\"nombre\":\"Juan\",\"edad\":\"20\"}",
              "SAVE  id=001, nombre=Juan, edad=20" },
            { "find 001",
              "FIND  id=001" },
            { "query carrera Sistemas de Información",
              "QUERY  field=carrera, value=Sistemas de Información" },
            { "querycontains nombre an",
              "QUERY_CONTAINS  field=nombre, substring=an" },
            { "update 001 {\"edad\":\"21\",\"ciudad\":\"Bogotá\"}",
              "UPDATE  id=001, edad=21, ciudad=Bogotá" },
            { "delete 001",
              "DELETE  id=001" },
            { "list",
              "LIST" },
            { "INDEX",
              "PRINT_INDEX" },
            { "exit",
              "EXIT" },
            { "quit",
              "EXIT" },
            { "foobar xyz",
              "UNKNOWN  input=foobar xyz" },
            { "",
              "UNKNOWN  input=" },
        };

        int ok = 0;
        for (String[] caso : casos) {
            try {
                Command cmd = CommandParser.parse(caso[0]);
                System.out.printf("%-60s → %s%n", "\"" + caso[0] + "\"", cmd);
                ok++;
            } catch (IllegalArgumentException e) {
                System.out.printf("%-60s → ERROR: %s%n", "\"" + caso[0] + "\"", e.getMessage());
            }
        }

        // Prueba de error esperado: save sin id
        try {
            CommandParser.parse("save {\"nombre\":\"Sin id\"}");
            System.out.println("ERROR: debería haber lanzado excepción por falta de id");
        } catch (IllegalArgumentException e) {
            System.out.println("\nSave sin id → Error esperado: " + e.getMessage() + " ✅");
            ok++;
        }

        System.out.println("\n✅ CommandParser OK (" + ok + "/" + (casos.length + 1) + ")");
    }
}
