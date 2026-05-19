package persistence;

import model.ObjectRecord;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JsonParser — Serialización y deserialización manual de ObjectRecord.
 *
 * Formato de un registro en archivo:
 *   {"id":"001","nombre":"Juan","edad":"20"}
 *
 * Restricciones:
 *  - Sin librerías externas de JSON.
 *  - Claves y valores siempre tratados como String.
 *  - El campo "id" siempre se escribe primero.
 *  - No soporta valores anidados ni arrays (el modelo es plano).
 */
public class JsonParser {

    // ------------------------------------------------------------------ //
    //  SERIALIZACIÓN: ObjectRecord → String JSON                          //
    // ------------------------------------------------------------------ //

    /**
     * Convierte un ObjectRecord en una línea JSON.
     *
     * @param record el registro a serializar; no debe ser nulo.
     * @return cadena JSON, por ejemplo: {"id":"001","nombre":"Juan","edad":"20"}
     * @throws IllegalArgumentException si record es nulo.
     */
    public static String serialize(ObjectRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("El registro no puede ser nulo.");
        }

        StringBuilder sb = new StringBuilder("{");

        // El campo "id" siempre va primero (convención del proyecto)
        sb.append(jsonPair("id", record.getId()));

        // Resto de atributos en el orden que devuelve toMap()
        for (Map.Entry<String, String> entry : record.toMap().entrySet()) {
            String key = entry.getKey();
            if (key.equals("id")) continue; // ya fue escrito
            sb.append(",");
            sb.append(jsonPair(key, entry.getValue()));
        }

        sb.append("}");
        return sb.toString();
    }

    // ------------------------------------------------------------------ //
    //  DESERIALIZACIÓN: String JSON → ObjectRecord                        //
    // ------------------------------------------------------------------ //

    /**
     * Convierte una línea JSON en un ObjectRecord.
     *
     * Asume que la línea tiene el formato plano:
     *   {"clave1":"valor1","clave2":"valor2",...}
     *
     * @param json cadena JSON de una sola línea; no debe ser nula ni vacía.
     * @return ObjectRecord reconstruido.
     * @throws IllegalArgumentException si el JSON es inválido o falta el campo "id".
     */
    public static ObjectRecord deserialize(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("La cadena JSON no puede ser nula o vacía.");
        }

        String trimmed = json.trim();

        // Validar llaves de apertura y cierre
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            throw new IllegalArgumentException("JSON mal formado (faltan llaves): " + json);
        }

        // Quitar llaves externas
        String content = trimmed.substring(1, trimmed.length() - 1).trim();

        // Parsear pares clave-valor
        Map<String, String> fields = parseFields(content);

        String id = fields.get("id");
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("El JSON no contiene el campo obligatorio 'id': " + json);
        }

        // Construir el ObjectRecord
        ObjectRecord record = new ObjectRecord(id);
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            if (!entry.getKey().equals("id")) {
                record.setAttribute(entry.getKey(), entry.getValue());
            }
        }
        return record;
    }

    // ------------------------------------------------------------------ //
    //  MÉTODOS PRIVADOS DE PARSEO                                         //
    // ------------------------------------------------------------------ //

    /**
     * Parsea el contenido interior de un objeto JSON plano
     * (sin llaves externas) y retorna un mapa ordenado de campos.
     *
     * Soporta valores que contengan comas escapadas dentro de comillas.
     */
    private static Map<String, String> parseFields(String content) {
        Map<String, String> fields = new LinkedHashMap<>();

        int i = 0;
        int len = content.length();

        while (i < len) {
            // Saltar espacios
            while (i < len && content.charAt(i) == ' ') i++;
            if (i >= len) break;

            // Leer clave (entre comillas)
            if (content.charAt(i) != '"') {
                throw new IllegalArgumentException("Se esperaba '\"' al inicio de clave en posición " + i);
            }
            int keyStart = i + 1;
            int keyEnd   = nextUnescapedQuote(content, keyStart);
            String key   = content.substring(keyStart, keyEnd);
            i = keyEnd + 1;

            // Saltar ':'
            while (i < len && content.charAt(i) == ' ') i++;
            if (i >= len || content.charAt(i) != ':') {
                throw new IllegalArgumentException("Se esperaba ':' después de la clave '" + key + "'");
            }
            i++; // consumir ':'

            // Saltar espacios
            while (i < len && content.charAt(i) == ' ') i++;

            // Leer valor (siempre entre comillas en nuestro formato)
            if (i >= len || content.charAt(i) != '"') {
                throw new IllegalArgumentException("Se esperaba '\"' al inicio del valor de '" + key + "'");
            }
            int valStart = i + 1;
            int valEnd   = nextUnescapedQuote(content, valStart);
            String value = content.substring(valStart, valEnd);
            i = valEnd + 1;

            fields.put(key, value);

            // Saltar coma separadora
            while (i < len && content.charAt(i) == ' ') i++;
            if (i < len && content.charAt(i) == ',') i++;
        }

        return fields;
    }

    /**
     * Encuentra el índice de la próxima comilla doble no escapada
     * a partir de la posición {@code from} en {@code s}.
     */
    private static int nextUnescapedQuote(String s, int from) {
        int i = from;
        while (i < s.length()) {
            if (s.charAt(i) == '\\') {
                i += 2; // saltar carácter escapado
            } else if (s.charAt(i) == '"') {
                return i;
            } else {
                i++;
            }
        }
        throw new IllegalArgumentException("Cadena JSON sin cierre de comilla a partir de posición " + from);
    }

    /**
     * Formatea un par clave-valor al estilo JSON:  "clave":"valor"
     * Escapa los caracteres especiales del valor.
     */
    private static String jsonPair(String key, String value) {
        return "\"" + escapeJson(key) + "\":\"" + escapeJson(value) + "\"";
    }

    /**
     * Escapa caracteres especiales para incluirlos dentro de una cadena JSON:
     * backslash, comillas dobles, saltos de línea, retornos de carro y tabulaciones.
     */
    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    // ------------------------------------------------------------------ //
    //  PRUEBAS BÁSICAS INTERNAS (main de referencia)                      //
    // ------------------------------------------------------------------ //

    public static void main(String[] args) {
        System.out.println("=== Pruebas de JsonParser ===\n");

        // --- Serializar ---
        ObjectRecord r1 = new ObjectRecord("001");
        r1.setAttribute("nombre", "Juan");
        r1.setAttribute("edad", "20");
        r1.setAttribute("ciudad", "Bogotá");

        String json1 = serialize(r1);
        System.out.println("Serializado:   " + json1);

        // --- Deserializar ---
        ObjectRecord r2 = deserialize(json1);
        System.out.println("Deserializado: id=" + r2.getId()
                + ", nombre=" + r2.getAttribute("nombre")
                + ", edad="   + r2.getAttribute("edad")
                + ", ciudad=" + r2.getAttribute("ciudad"));

        // --- Caso con caracteres especiales ---
        ObjectRecord r3 = new ObjectRecord("002");
        r3.setAttribute("descripcion", "Tiene \"comillas\" y \\backslash");
        String json3 = serialize(r3);
        System.out.println("\nEspeciales serializado:   " + json3);
        ObjectRecord r4 = deserialize(json3);
        System.out.println("Especiales deserializado: " + r4.getAttribute("descripcion"));

        System.out.println("\n✅ JsonParser OK");
    }
}
