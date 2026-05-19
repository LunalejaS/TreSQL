package btree;

/**
 * Par clave-valor almacenado en cada entrada del Árbol B.
 *
 * La clave (key) es un String que actúa como identificador único del objeto.
 * El valor (value) es un String que representa el objeto serializado en JSON.
 *
 * Esta clase es inmutable en su clave y mutable en su valor,
 * lo que permite actualizaciones sin mover la entrada dentro del árbol.
 */
public class KeyValue {

    /** Clave única del objeto (campo "id"). No puede ser nula ni vacía. */
    private final String key;

    /** Valor asociado a la clave, representado como String JSON. */
    private String value;

    // =========================================================================
    // Constructor
    // =========================================================================

    /**
     * Construye un par clave-valor.
     *
     * @param key   Clave única del objeto. No puede ser nula ni vacía.
     * @param value Valor JSON asociado. Puede ser null si solo se usa la clave como puntero.
     * @throws IllegalArgumentException si la clave es nula o vacía.
     */
    public KeyValue(String key, String value) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("La clave no puede ser nula ni vacía.");
        }
        this.key   = key;
        this.value = value;
    }

    // =========================================================================
    // Getters y setter
    // =========================================================================

    /**
     * Retorna la clave del par.
     *
     * @return Clave (String no nulo).
     */
    public String getKey() {
        return key;
    }

    /**
     * Retorna el valor JSON asociado a la clave.
     *
     * @return Valor en formato String JSON, o null si no se asignó.
     */
    public String getValue() {
        return value;
    }

    /**
     * Actualiza el valor asociado a la clave.
     * La clave permanece inmutable; solo el valor cambia.
     *
     * @param value Nuevo valor JSON.
     */
    public void setValue(String value) {
        this.value = value;
    }

    // =========================================================================
    // Utilidades
    // =========================================================================

    /**
     * Compara lexicográficamente esta clave con otra.
     * Retorna negativo si esta clave es menor, 0 si son iguales, positivo si es mayor.
     *
     * @param otherKey Clave a comparar.
     * @return Resultado de comparación lexicográfica.
     */
    public int compareKeyTo(String otherKey) {
        return this.key.compareTo(otherKey);
    }

    @Override
    public String toString() {
        return "KeyValue{key='" + key + "', value='" + value + "'}";
    }
}
