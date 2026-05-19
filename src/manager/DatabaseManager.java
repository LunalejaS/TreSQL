package manager;

import btree.BTree;
import model.ObjectRecord;
import persistence.FileManager;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * DatabaseManager — Coordinador central del gestor NoSQL.
 *
 * Orquesta las tres capas de la lógica de negocio:
 *  1. {@link BTree}       — índice en memoria para búsquedas rápidas por {@code id} (O(log n)).
 *  2. {@link FileManager} — persistencia real de los registros en {@code database.json}.
 *  3. {@link QueryEngine} — búsquedas lineales por atributos secundarios.
 *
 * Al iniciar, reconstruye el árbol B desde el archivo (sin snapshot de índice).
 * Toda operación de escritura actualiza simultáneamente el árbol y el archivo
 * para garantizar consistencia entre ambas estructuras.
 *
 * Flujo de operaciones:
 *  - save:     árbol.insert  + file.save
 *  - findById: árbol.search  → si existe → file.findById
 *  - update:   árbol.search  → si existe → file.update
 *  - delete:   árbol.search  → si existe → árbol.delete + file.delete
 *  - query:    queryEngine.filterByAttribute  (sin árbol)
 *  - list:     queryEngine.findAll            (sin árbol)
 */
public class DatabaseManager {

    // ------------------------------------------------------------------ //
    //  ESTADO                                                             //
    // ------------------------------------------------------------------ //

    /** Índice en memoria. Guarda el id como clave y como valor (el árbol indexa ids). */
    private final BTree index;

    /** Capa de persistencia. */
    private final FileManager fileManager;

    /** Motor de consultas por atributos secundarios. */
    private final QueryEngine queryEngine;

    // ------------------------------------------------------------------ //
    //  CONSTRUCTORES                                                      //
    // ------------------------------------------------------------------ //

    /**
     * Crea un DatabaseManager apuntando al archivo por defecto ({@code data/database.json})
     * y reconstruye el árbol B cargando todos los registros existentes.
     *
     * @throws IOException si hay un error al leer el archivo durante la reconstrucción.
     */
    public DatabaseManager() throws IOException {
        this(new FileManager());
    }

    /**
     * Crea un DatabaseManager con una instancia de FileManager ya configurada.
     * Permite inyectar un FileManager con ruta personalizada (útil en tests).
     *
     * @param fileManager instancia de FileManager; no debe ser nula.
     * @throws IOException si hay un error al leer el archivo durante la reconstrucción.
     */
    public DatabaseManager(FileManager fileManager) throws IOException {
        if (fileManager == null) {
            throw new IllegalArgumentException("FileManager no puede ser nulo.");
        }
        this.fileManager  = fileManager;
        this.index        = new BTree();
        this.queryEngine  = new QueryEngine(fileManager);

        rebuildIndex();
    }

    // ------------------------------------------------------------------ //
    //  OPERACIONES CRUD                                                   //
    // ------------------------------------------------------------------ //

    /**
     * Guarda un nuevo registro en el sistema.
     *
     * Verifica unicidad del {@code id} en el árbol B (O(log n)) antes de
     * escribir en disco, lo que evita lecturas innecesarias del archivo.
     *
     * @param record registro a guardar; no debe ser nulo.
     * @throws IllegalArgumentException si {@code record} es nulo.
     * @throws IllegalStateException    si ya existe un registro con ese {@code id}.
     * @throws IOException              si hay un error de E/S al escribir.
     */
    public void save(ObjectRecord record) throws IOException {
        if (record == null) {
            throw new IllegalArgumentException("El registro no puede ser nulo.");
        }

        // Verificar duplicado en O(log n) usando el árbol
        if (index.search(record.getId()) != null) {
            throw new IllegalStateException(
                    "Ya existe un registro con id='" + record.getId() + "'.");
        }

        // Persistir primero; si falla, el árbol no se modifica (consistencia)
        fileManager.save(record);

        // Actualizar índice en memoria
        index.insert(record.getId(), record.getId());
    }

    /**
     * Busca un registro por su {@code id}.
     *
     * Usa el árbol B para verificar existencia (O(log n)) antes de acceder
     * al archivo, evitando lecturas completas cuando el registro no existe.
     *
     * @param id identificador a buscar; no debe ser nulo ni vacío.
     * @return {@code Optional} con el registro si existe, o vacío si no.
     * @throws IllegalArgumentException si {@code id} es nulo o vacío.
     * @throws IOException              si hay un error de E/S al leer.
     */
    public Optional<ObjectRecord> findById(String id) throws IOException {
        validateId(id);

        // Consulta rápida al árbol primero
        if (index.search(id) == null) {
            return Optional.empty();
        }

        // El árbol confirmó existencia; leer desde archivo
        return fileManager.findById(id);
    }

    /**
     * Actualiza los atributos de un registro existente.
     *
     * Solo se modifican los campos presentes en {@code newAttributes}.
     * El campo {@code "id"} se ignora aunque esté en el mapa.
     * El árbol B no necesita actualizarse (las claves son los ids, que son inmutables).
     *
     * @param id            identificador del registro a modificar.
     * @param newAttributes mapa con los campos a actualizar; no debe ser nulo.
     * @return {@code true} si el registro fue encontrado y actualizado.
     * @throws IllegalArgumentException si {@code id} es nulo/vacío o {@code newAttributes} es nulo.
     * @throws IllegalStateException    si no existe ningún registro con ese {@code id}.
     * @throws IOException              si hay un error de E/S al escribir.
     */
    public boolean update(String id, Map<String, String> newAttributes) throws IOException {
        validateId(id);
        if (newAttributes == null) {
            throw new IllegalArgumentException("El mapa de atributos no puede ser nulo.");
        }

        // Verificar existencia con el árbol antes de ir al archivo
        if (index.search(id) == null) {
            throw new IllegalStateException(
                    "No existe ningún registro con id='" + id + "'.");
        }

        return fileManager.update(id, newAttributes);
    }

    /**
     * Elimina un registro del sistema.
     *
     * Actualiza tanto el árbol B como el archivo para mantener consistencia.
     *
     * @param id identificador del registro a eliminar.
     * @return {@code true} si el registro existía y fue eliminado; {@code false} si no existía.
     * @throws IllegalArgumentException si {@code id} es nulo o vacío.
     * @throws IOException              si hay un error de E/S al escribir.
     */
    public boolean delete(String id) throws IOException {
        validateId(id);

        // Si el árbol no lo tiene, no está en el sistema
        if (index.search(id) == null) {
            return false;
        }

        // Eliminar del archivo primero; si falla, el árbol conserva el id (consistencia)
        boolean deleted = fileManager.delete(id);

        if (deleted) {
            index.delete(id);
        }

        return deleted;
    }

    // ------------------------------------------------------------------ //
    //  CONSULTAS                                                          //
    // ------------------------------------------------------------------ //

    /**
     * Busca registros por un atributo secundario (comparación exacta).
     *
     * Realiza una lectura completa del archivo O(n); no usa el árbol B.
     *
     * @param field nombre del atributo por el que filtrar.
     * @param value valor exacto que debe tener el atributo.
     * @return lista de registros que cumplen la condición; puede estar vacía.
     * @throws IOException si hay un error de E/S al leer.
     */
    public List<ObjectRecord> query(String field, String value) throws IOException {
        return queryEngine.filterByAttribute(field, value);
    }

    /**
     * Busca registros cuyo atributo {@code field} contenga la subcadena indicada
     * (case-insensitive).
     *
     * @param field     nombre del atributo donde buscar.
     * @param substring subcadena a buscar.
     * @return lista de registros que cumplen la condición; puede estar vacía.
     * @throws IOException si hay un error de E/S al leer.
     */
    public List<ObjectRecord> queryContains(String field, String substring) throws IOException {
        return queryEngine.filterByAttributeContains(field, substring);
    }

    /**
     * Retorna todos los registros almacenados.
     *
     * @return lista completa de registros; puede estar vacía.
     * @throws IOException si hay un error de E/S al leer.
     */
    public List<ObjectRecord> listAll() throws IOException {
        return queryEngine.findAll();
    }

    // ------------------------------------------------------------------ //
    //  INFORMACIÓN DEL ÍNDICE                                             //
    // ------------------------------------------------------------------ //

    /**
     * Retorna el número de registros actualmente indexados en el árbol B.
     *
     * @return número de claves en el árbol.
     */
    public int size() {
        return index.getTotalKeys();
    }

    /**
     * Imprime el árbol B en consola (útil para debug y demostración).
     * Delega en {@link BTree#printTree()}.
     */
    public void printIndex() {
        System.out.println("=== Árbol B (índice en memoria) ===");
        index.printTree();
        System.out.println("Total de claves: " + index.getTotalKeys());
    }

    // ------------------------------------------------------------------ //
    //  MÉTODOS PRIVADOS DE APOYO                                          //
    // ------------------------------------------------------------------ //

    /**
     * Reconstruye el árbol B cargando todos los ids desde el archivo.
     * Se invoca en el constructor; no requiere orden particular de inserción.
     */
    private void rebuildIndex() throws IOException {
        List<ObjectRecord> records = fileManager.readAll();
        for (ObjectRecord record : records) {
            index.insert(record.getId(), record.getId());
        }
        System.out.println("[DatabaseManager] Índice reconstruido con "
                + index.getTotalKeys() + " registro(s).");
    }

    /**
     * Valida que un id no sea nulo ni vacío.
     *
     * @throws IllegalArgumentException si {@code id} es inválido.
     */
    private void validateId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("El id no puede ser nulo ni vacío.");
        }
    }

    // ------------------------------------------------------------------ //
    //  PRUEBAS BÁSICAS (main de referencia)                               //
    // ------------------------------------------------------------------ //

    public static void main(String[] args) throws IOException {
        System.out.println("=== Pruebas de DatabaseManager ===\n");

        // Usar archivo de prueba aislado
        FileManager fm = new FileManager("data/test_manager.json");
        DatabaseManager db = new DatabaseManager(fm);

        // ---------- SAVE ----------
        System.out.println("--- save ---");
        ObjectRecord r1 = new ObjectRecord("001");
        r1.setAttribute("nombre", "Juan");
        r1.setAttribute("edad", "20");
        r1.setAttribute("carrera", "Sistemas");
        db.save(r1);

        ObjectRecord r2 = new ObjectRecord("002");
        r2.setAttribute("nombre", "Ana");
        r2.setAttribute("edad", "22");
        r2.setAttribute("carrera", "Matemáticas");
        db.save(r2);

        ObjectRecord r3 = new ObjectRecord("003");
        r3.setAttribute("nombre", "Carlos");
        r3.setAttribute("edad", "21");
        r3.setAttribute("carrera", "Sistemas");
        db.save(r3);

        ObjectRecord r4 = new ObjectRecord("004");
        r4.setAttribute("nombre", "Luisa");
        r4.setAttribute("edad", "23");
        r4.setAttribute("carrera", "Física");
        db.save(r4);

        System.out.println("Guardados 4 registros. Tamaño: " + db.size());

        // ---------- PRINT INDEX ----------
        System.out.println();
        db.printIndex();

        // ---------- FIND BY ID ----------
        System.out.println("\n--- findById ---");
        db.findById("002").ifPresentOrElse(
                r -> System.out.println("Encontrado: " + r),
                ()  -> System.out.println("No encontrado"));

        db.findById("999").ifPresentOrElse(
                r -> System.out.println("Encontrado: " + r),
                ()  -> System.out.println("id='999' → No encontrado ✅"));

        // ---------- UPDATE ----------
        System.out.println("\n--- update ---");
        db.update("001", Map.of("edad", "21", "ciudad", "Bogotá"));
        db.findById("001").ifPresent(r -> System.out.println("Actualizado: " + r));

        // update de id inexistente
        try {
            db.update("999", Map.of("nombre", "X"));
        } catch (IllegalStateException e) {
            System.out.println("Error esperado: " + e.getMessage() + " ✅");
        }

        // ---------- QUERY EXACTO ----------
        System.out.println("\n--- query exacto: carrera=Sistemas ---");
        db.query("carrera", "Sistemas")
          .forEach(r -> System.out.println("  " + r));

        // ---------- QUERY CONTAINS ----------
        System.out.println("\n--- queryContains: nombre contiene 'a' ---");
        db.queryContains("nombre", "a")
          .forEach(r -> System.out.println("  " + r));

        // ---------- LIST ALL ----------
        System.out.println("\n--- listAll ---");
        db.listAll().forEach(r -> System.out.println("  " + r));

        // ---------- DELETE ----------
        System.out.println("\n--- delete ---");
        boolean del = db.delete("003");
        System.out.println("Eliminado '003': " + del);
        System.out.println("Tamaño tras delete: " + db.size());

        boolean delAgain = db.delete("003");
        System.out.println("Eliminar '003' de nuevo: " + delAgain + " (esperado false) ✅");

        // ---------- DUPLICADO ----------
        System.out.println("\n--- Prueba id duplicado ---");
        try {
            ObjectRecord dup = new ObjectRecord("001");
            dup.setAttribute("nombre", "Duplicado");
            db.save(dup);
        } catch (IllegalStateException e) {
            System.out.println("Error esperado: " + e.getMessage() + " ✅");
        }

        // ---------- PERSISTENCIA: reiniciar y verificar ---
        System.out.println("\n--- Reinicio: nueva instancia de DatabaseManager ---");
        DatabaseManager db2 = new DatabaseManager(fm);
        System.out.println("Registros tras reinicio: " + db2.size() + " (esperado 3)");
        db2.listAll().forEach(r -> System.out.println("  " + r));

        System.out.println("\n✅ DatabaseManager OK");
    }
}
