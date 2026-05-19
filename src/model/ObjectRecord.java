package model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ObjectRecord — Unidad básica de almacenamiento del gestor NoSQL.
 *
 * Representa un objeto con un identificador único inmutable ({@code id})
 * y un conjunto de atributos arbitrarios como pares String→String.
 *
 * Invariantes:
 *  - {@code id} nunca es nulo ni vacío.
 *  - {@code id} no puede modificarse una vez construido el registro.
 *  - {@code attributes} no contiene el campo "id" (se gestiona por separado).
 *  - El orden de inserción de atributos se preserva (LinkedHashMap).
 */
public class ObjectRecord {

    // ------------------------------------------------------------------ //
    //  ESTADO                                                             //
    // ------------------------------------------------------------------ //

    /** Identificador único e inmutable del registro. */
    private final String id;

    /**
     * Atributos del registro. El campo "id" nunca se almacena aquí;
     * se gestiona a través del campo {@code id} directamente.
     */
    private final Map<String, String> attributes;

    // ------------------------------------------------------------------ //
    //  CONSTRUCTOR                                                        //
    // ------------------------------------------------------------------ //

    /**
     * Crea un ObjectRecord con el identificador indicado y sin atributos.
     *
     * @param id identificador único del registro; no puede ser nulo ni vacío.
     * @throws IllegalArgumentException si {@code id} es nulo o vacío.
     */
    public ObjectRecord(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(
                    "El id de un ObjectRecord no puede ser nulo ni vacío.");
        }
        this.id         = id.trim();
        this.attributes = new LinkedHashMap<>();
    }

    // ------------------------------------------------------------------ //
    //  ACCESO AL ID                                                       //
    // ------------------------------------------------------------------ //

    /**
     * Retorna el identificador único e inmutable del registro.
     *
     * @return el {@code id}; nunca nulo ni vacío.
     */
    public String getId() {
        return id;
    }

    // ------------------------------------------------------------------ //
    //  OPERACIONES SOBRE ATRIBUTOS                                        //
    // ------------------------------------------------------------------ //

    /**
     * Retorna el valor del atributo indicado, o {@code null} si no existe.
     *
     * @param key nombre del atributo; no debe ser nulo.
     * @return el valor asociado, o {@code null} si la clave no existe.
     */
    public String getAttribute(String key) {
        if (key == null) return null;
        return attributes.get(key);
    }

    /**
     * Establece o sobreescribe un atributo del registro.
     *
     * La clave {@code "id"} se ignora silenciosamente: el id es inmutable
     * y no puede modificarse a través de este método.
     *
     * @param key   nombre del atributo; no debe ser nulo ni vacío.
     * @param value valor del atributo; puede ser cadena vacía, no nulo.
     * @throws IllegalArgumentException si {@code key} es nulo/vacío o {@code value} es nulo.
     */
    public void setAttribute(String key, String value) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("La clave de atributo no puede ser nula ni vacía.");
        }
        if (value == null) {
            throw new IllegalArgumentException(
                    "El valor del atributo '" + key + "' no puede ser nulo.");
        }
        // El campo "id" es inmutable; se ignora para evitar inconsistencias
        if (key.equals("id")) return;
        attributes.put(key, value);
    }

    /**
     * Elimina un atributo del registro.
     *
     * @param key nombre del atributo a eliminar.
     * @return {@code true} si el atributo existía y fue eliminado; {@code false} en caso contrario.
     */
    public boolean removeAttribute(String key) {
        if (key == null || key.equals("id")) return false;
        return attributes.remove(key) != null;
    }

    /**
     * Indica si el registro contiene el atributo indicado.
     *
     * @param key nombre del atributo.
     * @return {@code true} si el atributo existe.
     */
    public boolean hasAttribute(String key) {
        if (key == null) return false;
        return attributes.containsKey(key);
    }

    // ------------------------------------------------------------------ //
    //  CONVERSIÓN A MAPA                                                  //
    // ------------------------------------------------------------------ //

    /**
     * Retorna una vista de solo lectura del mapa de atributos.
     *
     * <b>El campo {@code id} NO está incluido en este mapa.</b>
     * {@link persistence.JsonParser} lo trata por separado para garantizar
     * que siempre se serialice en primera posición.
     *
     * @return mapa inmutable de atributos; puede estar vacío, nunca nulo.
     */
    public Map<String, String> toMap() {
        return Collections.unmodifiableMap(attributes);
    }

    /**
     * Carga múltiples atributos desde un mapa externo.
     * Equivale a llamar {@link #setAttribute} para cada entrada.
     * El campo {@code "id"} del mapa se ignora.
     *
     * @param map mapa de atributos a cargar; no debe ser nulo.
     */
    public void fromMap(Map<String, String> map) {
        if (map == null) {
            throw new IllegalArgumentException("El mapa de atributos no puede ser nulo.");
        }
        for (Map.Entry<String, String> entry : map.entrySet()) {
            setAttribute(entry.getKey(), entry.getValue());
        }
    }

    // ------------------------------------------------------------------ //
    //  MÉTODOS ESTÁNDAR                                                   //
    // ------------------------------------------------------------------ //

    /**
     * Representación legible del registro para debug.
     * Formato: ObjectRecord{id='001', nombre='Juan', edad='20'}
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ObjectRecord{id='").append(id).append("'");
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            sb.append(", ").append(entry.getKey()).append("='").append(entry.getValue()).append("'");
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * Dos registros son iguales si tienen el mismo {@code id}.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ObjectRecord)) return false;
        return id.equals(((ObjectRecord) o).id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    // ------------------------------------------------------------------ //
    //  PRUEBAS BÁSICAS (main de referencia)                               //
    // ------------------------------------------------------------------ //

    public static void main(String[] args) {
        System.out.println("=== Pruebas de ObjectRecord ===\n");

        // Construcción válida
        ObjectRecord r1 = new ObjectRecord("001");
        r1.setAttribute("nombre", "Juan");
        r1.setAttribute("edad", "20");
        r1.setAttribute("ciudad", "Bogotá");
        System.out.println("Creado:  " + r1);
        System.out.println("getAttribute('nombre'): " + r1.getAttribute("nombre"));
        System.out.println("getAttribute('x'):      " + r1.getAttribute("x") + " (esperado null)");
        System.out.println("hasAttribute('edad'):   " + r1.hasAttribute("edad"));

        // toMap no incluye id
        System.out.println("toMap(): " + r1.toMap());
        System.out.println("id en toMap: " + r1.toMap().containsKey("id") + " (esperado false)");

        // setAttribute ignora "id"
        r1.setAttribute("id", "MODIFICADO");
        System.out.println("Tras setAttribute('id','MODIFICADO'): id=" + r1.getId() + " (sin cambio)");

        // fromMap
        ObjectRecord r2 = new ObjectRecord("002");
        java.util.Map<String, String> mapa = new java.util.LinkedHashMap<>();
        mapa.put("nombre", "Ana");
        mapa.put("carrera", "Sistemas");
        mapa.put("id", "IGNORADO");
        r2.fromMap(mapa);
        System.out.println("\nfromMap: " + r2);

        // removeAttribute
        r2.removeAttribute("carrera");
        System.out.println("Tras removeAttribute('carrera'): " + r2);

        // equals por id
        ObjectRecord r3 = new ObjectRecord("001");
        System.out.println("\nr1.equals(r3) [mismo id]: " + r1.equals(r3) + " (esperado true)");
        System.out.println("r1.equals(r2) [distinto id]: " + r1.equals(r2) + " (esperado false)");

        // Constructor inválido
        try {
            new ObjectRecord("");
        } catch (IllegalArgumentException e) {
            System.out.println("\nError esperado (id vacío): " + e.getMessage() + " ✅");
        }
        try {
            new ObjectRecord(null);
        } catch (IllegalArgumentException e) {
            System.out.println("Error esperado (id nulo):  " + e.getMessage() + " ✅");
        }

        System.out.println("\n✅ ObjectRecord OK");
    }
}
