package cli;

import java.util.Collections;
import java.util.Map;

/**
 * Command — Objeto de datos que representa un comando ya parseado.
 *
 * Producido por {@link CommandParser} y consumido por {@link DatabaseShell}.
 * Es inmutable una vez construido.
 *
 * Estructura:
 *   - type   : qué operación ejecutar
 *   - params : argumentos de la operación (puede estar vacío)
 *
 * Claves estándar usadas en params según CommandType:
 *
 *   SAVE           → todas las claves del JSON inline (incluye "id")
 *   FIND           → "id"
 *   QUERY          → "field", "value"
 *   QUERY_CONTAINS → "field", "substring"
 *   UPDATE         → "id" + claves del JSON inline de atributos
 *   DELETE         → "id"
 *   LIST           → (vacío)
 *   PRINT_INDEX    → (vacío)
 *   EXIT           → (vacío)
 *   UNKNOWN        → "input" (línea original para mostrar en error)
 */
public class Command {

    private final CommandType type;
    private final Map<String, String> params;

    /**
     * @param type   tipo de comando; no debe ser nulo.
     * @param params mapa de parámetros; puede estar vacío, no nulo.
     */
    public Command(CommandType type, Map<String, String> params) {
        if (type == null)   throw new IllegalArgumentException("CommandType no puede ser nulo.");
        if (params == null) throw new IllegalArgumentException("params no puede ser nulo.");
        this.type   = type;
        this.params = Collections.unmodifiableMap(params);
    }

    public CommandType getType() { return type; }

    /**
     * @param key clave del parámetro.
     * @return valor asociado, o {@code null} si no existe.
     */
    public String getParam(String key) { return params.get(key); }

    /** @return vista inmutable de todos los parámetros. */
    public Map<String, String> getParams() { return params; }

    @Override
    public String toString() {
        return "Command{type=" + type + ", params=" + params + "}";
    }
}
