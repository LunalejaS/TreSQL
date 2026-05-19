package manager;

import model.ObjectRecord;
import persistence.FileManager;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * QueryEngine — Motor de consultas sobre atributos secundarios.
 *
 * Realiza búsquedas lineales O(n) sobre todos los registros del archivo.
 * No utiliza el árbol B (que solo indexa el campo {@code id}).
 *
 * Responsabilidades:
 *  - Filtrar registros por cualquier campo distinto de {@code id}.
 *  - Delegar la lectura completa de datos a {@link FileManager}.
 *
 * Limitaciones actuales (v1):
 *  - Solo soporta comparación exacta (equals), no rangos ni expresiones regulares.
 *  - Toda consulta implica leer el archivo completo (sin índice secundario).
 */
public class QueryEngine {

    // ------------------------------------------------------------------ //
    //  ESTADO                                                             //
    // ------------------------------------------------------------------ //

    /** Capa de persistencia; provee acceso a todos los registros. */
    private final FileManager fileManager;

    // ------------------------------------------------------------------ //
    //  CONSTRUCTOR                                                        //
    // ------------------------------------------------------------------ //

    /**
     * @param fileManager instancia de FileManager ya inicializada; no debe ser nula.
     */
    public QueryEngine(FileManager fileManager) {
        if (fileManager == null) {
            throw new IllegalArgumentException("FileManager no puede ser nulo.");
        }
        this.fileManager = fileManager;
    }

    // ------------------------------------------------------------------ //
    //  CONSULTAS                                                          //
    // ------------------------------------------------------------------ //

    /**
     * Retorna todos los registros cuyo atributo {@code field} tiene
     * exactamente el valor {@code value} (comparación exacta, case-sensitive).
     *
     * @param field nombre del atributo a comparar (puede ser {@code "id"}).
     * @param value valor exacto que debe tener el atributo.
     * @return lista (puede estar vacía) de registros que cumplen la condición.
     * @throws IOException              si hay un error de E/S al leer el archivo.
     * @throws IllegalArgumentException si {@code field} o {@code value} son nulos.
     */
    public List<ObjectRecord> filterByAttribute(String field, String value) throws IOException {
        if (field == null || field.isBlank()) {
            throw new IllegalArgumentException("El campo de búsqueda no puede ser nulo ni vacío.");
        }
        if (value == null) {
            throw new IllegalArgumentException("El valor de búsqueda no puede ser nulo.");
        }

        return fileManager.readAll().stream()
                .filter(r -> matchesField(r, field, value))
                .collect(Collectors.toList());
    }

    /**
     * Retorna todos los registros cuyo atributo {@code field} contiene
     * la subcadena {@code substring} (búsqueda parcial, case-insensitive).
     *
     * @param field     nombre del atributo donde buscar.
     * @param substring subcadena a buscar dentro del valor del atributo.
     * @return lista de registros que cumplen la condición.
     * @throws IOException si hay un error de E/S al leer el archivo.
     */
    public List<ObjectRecord> filterByAttributeContains(String field, String substring)
            throws IOException {
        if (field == null || field.isBlank()) {
            throw new IllegalArgumentException("El campo de búsqueda no puede ser nulo ni vacío.");
        }
        if (substring == null) {
            throw new IllegalArgumentException("La subcadena no puede ser nula.");
        }

        String sub = substring.toLowerCase();
        return fileManager.readAll().stream()
                .filter(r -> {
                    String val = getFieldValue(r, field);
                    return val != null && val.toLowerCase().contains(sub);
                })
                .collect(Collectors.toList());
    }

    /**
     * Retorna todos los registros almacenados en el archivo.
     * Equivale a un SELECT * sin filtros.
     *
     * @return lista completa de registros; puede estar vacía.
     * @throws IOException si hay un error de E/S.
     */
    public List<ObjectRecord> findAll() throws IOException {
        return fileManager.readAll();
    }

    // ------------------------------------------------------------------ //
    //  MÉTODOS PRIVADOS DE APOYO                                          //
    // ------------------------------------------------------------------ //

    /**
     * Determina si un registro cumple la condición field = value.
     * Soporta el campo especial {@code "id"} además de los atributos normales.
     */
    private boolean matchesField(ObjectRecord record, String field, String value) {
        String fieldValue = getFieldValue(record, field);
        return value.equals(fieldValue);
    }

    /**
     * Obtiene el valor de un campo de un registro, incluyendo el campo especial {@code "id"}.
     *
     * @return el valor del campo, o {@code null} si el campo no existe en el registro.
     */
    private String getFieldValue(ObjectRecord record, String field) {
        if ("id".equals(field)) {
            return record.getId();
        }
        return record.getAttribute(field);
    }
}
