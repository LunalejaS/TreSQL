package persistence;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import model.ObjectRecord;

/**
 * FileManager — Capa de persistencia sobre {@code database.json}.
 *
 * Formato del archivo: un objeto JSON por línea (NDJSON).
 *   {"id":"001","nombre":"Juan","edad":"20"}
 *   {"id":"002","nombre":"Ana","ciudad":"Medellín"}
 *
 * Decisiones de diseño (según documento de continuidad v2.0):
 *  - Ruta por defecto: data/database.json
 *  - Cada operación de escritura reescribe el archivo completo
 *    para garantizar consistencia.
 *  - Delega serialización/deserialización a JsonParser.
 *  - Sin librerías externas; usa sólo java.io y java.nio.
 */
public class FileManager {

    // ------------------------------------------------------------------ //
    //  CONSTANTES Y ESTADO                                                //
    // ------------------------------------------------------------------ //

    /** Ruta predeterminada del archivo de datos. */
    private static final String DEFAULT_PATH = "data/database.json";

    /** Ruta efectiva que usa esta instancia. */
    private final String filePath;

    // ------------------------------------------------------------------ //
    //  CONSTRUCTORES                                                      //
    // ------------------------------------------------------------------ //

    /**
     * Crea un FileManager apuntando al archivo por defecto
     * ({@value DEFAULT_PATH}).
     */
    public FileManager() {
        this(DEFAULT_PATH);
    }

    /**
     * Crea un FileManager apuntando a la ruta indicada.
     *
     * @param filePath ruta al archivo JSON; no debe ser nula.
     */
    public FileManager(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("La ruta del archivo no puede ser nula o vacía.");
        }
        this.filePath = filePath;
        ensureFileExists();
    }

    // ------------------------------------------------------------------ //
    //  OPERACIONES CRUD                                                   //
    // ------------------------------------------------------------------ //

    /**
     * Guarda un nuevo registro en el archivo.
     *
     * Si ya existe un registro con el mismo {@code id}, lanza una excepción
     * en lugar de sobreescribirlo (se debe usar {@link #update} para eso).
     *
     * @param record el registro a guardar; no debe ser nulo.
     * @throws IllegalArgumentException si {@code record} es nulo.
     * @throws IllegalStateException    si ya existe un registro con ese id.
     * @throws IOException              si hay un error de E/S.
     */
    public void save(ObjectRecord record) throws IOException {
        if (record == null) {
            throw new IllegalArgumentException("El registro no puede ser nulo.");
        }

        List<ObjectRecord> all = readAll();

        // Verificar id duplicado
        boolean exists = all.stream()
                .anyMatch(r -> r.getId().equals(record.getId()));
        if (exists) {
            throw new IllegalStateException(
                    "Ya existe un registro con id='" + record.getId() + "'. Use update() para modificarlo.");
        }

        all.add(record);
        writeAll(all);
    }

    /**
     * Lee todos los registros almacenados en el archivo.
     *
     * @return lista (puede estar vacía) de todos los ObjectRecord del archivo.
     * @throws IOException si hay un error de E/S.
     */
    public List<ObjectRecord> readAll() throws IOException {
        List<ObjectRecord> records = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(
                Paths.get(filePath), StandardCharsets.UTF_8)) {

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue; // ignorar líneas en blanco
                try {
                    records.add(JsonParser.deserialize(line));
                } catch (IllegalArgumentException e) {
                    // Línea corrupta: se avisa pero no se aborta la lectura
                    System.err.println("[FileManager] Línea ignorada (JSON inválido): " + line);
                }
            }
        }

        return records;
    }

    /**
     * Busca un registro por su {@code id}.
     *
     * @param id el identificador a buscar; no debe ser nulo ni vacío.
     * @return {@code Optional} con el registro si se encuentra, o vacío.
     * @throws IOException si hay un error de E/S.
     */
    public Optional<ObjectRecord> findById(String id) throws IOException {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("El id de búsqueda no puede ser nulo o vacío.");
        }

        return readAll().stream()
                .filter(r -> r.getId().equals(id))
                .findFirst();
    }

    /**
     * Actualiza los atributos de un registro existente.
     *
     * Solo se actualizan los campos presentes en {@code newAttributes};
     * el resto de atributos del registro se conservan intactos.
     * El campo {@code id} no puede modificarse mediante este método.
     *
     * @param id            identificador del registro a modificar.
     * @param newAttributes mapa con los campos a actualizar.
     * @return {@code true} si el registro fue encontrado y actualizado;
     *         {@code false} si no existía ningún registro con ese id.
     * @throws IOException si hay un error de E/S.
     */
    public boolean update(String id, Map<String, String> newAttributes) throws IOException {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("El id no puede ser nulo o vacío.");
        }
        if (newAttributes == null) {
            throw new IllegalArgumentException("El mapa de atributos no puede ser nulo.");
        }

        List<ObjectRecord> all = readAll();
        boolean found = false;

        for (ObjectRecord record : all) {
            if (record.getId().equals(id)) {
                // Actualizar sólo los campos del mapa recibido
                for (Map.Entry<String, String> entry : newAttributes.entrySet()) {
                    if (!entry.getKey().equals("id")) { // id es inmutable
                        record.setAttribute(entry.getKey(), entry.getValue());
                    }
                }
                found = true;
                break;
            }
        }

        if (found) {
            writeAll(all);
        }
        return found;
    }

    /**
     * Elimina el registro con el {@code id} indicado.
     *
     * @param id identificador del registro a eliminar.
     * @return {@code true} si el registro existía y fue eliminado;
     *         {@code false} si no se encontró.
     * @throws IOException si hay un error de E/S.
     */
    public boolean delete(String id) throws IOException {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("El id no puede ser nulo o vacío.");
        }

        List<ObjectRecord> all = readAll();
        int sizeBefore = all.size();

        all.removeIf(r -> r.getId().equals(id));

        boolean removed = all.size() < sizeBefore;
        if (removed) {
            writeAll(all);
        }
        return removed;
    }

    // ------------------------------------------------------------------ //
    //  MÉTODOS DE SOPORTE                                                 //
    // ------------------------------------------------------------------ //

    /**
     * Reescribe el archivo completo con la lista de registros proporcionada.
     *
     * Usa escritura atómica vía archivo temporal + rename para evitar
     * corrupción en caso de fallo durante la escritura.
     *
     * @param records lista de registros a persistir.
     * @throws IOException si hay un error de E/S.
     */
    public void writeAll(List<ObjectRecord> records) throws IOException {
        Path target = Paths.get(filePath);
        Path temp   = target.resolveSibling(target.getFileName() + ".tmp");

        try (BufferedWriter writer = Files.newBufferedWriter(
                temp, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {

            for (ObjectRecord record : records) {
                writer.write(JsonParser.serialize(record));
                writer.newLine();
            }
        }

        // Rename atómico: reemplaza el archivo original
        Files.move(temp, target,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
    }

    /**
     * Garantiza que el archivo (y su directorio padre) existan.
     * Crea el directorio y el archivo vacío si no existen.
     */
    public void ensureFileExists() {
        try {
            Path path = Paths.get(filePath);
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            if (!Files.exists(path)) {
                Files.createFile(path);
            }
        } catch (IOException e) {
            throw new RuntimeException(
                    "No se pudo crear el archivo de datos en: " + filePath, e);
        }
    }

    /** @return la ruta efectiva del archivo que gestiona esta instancia. */
    public String getFilePath() {
        return filePath;
    }

    // ------------------------------------------------------------------ //
    //  PRUEBAS BÁSICAS (main de referencia)                               //
    // ------------------------------------------------------------------ //

    public static void main(String[] args) throws IOException {
        System.out.println("=== Pruebas de FileManager ===\n");

        // Usar archivo temporal para las pruebas
        FileManager fm = new FileManager("data/test_db.json");

        // ---------- SAVE ----------
        System.out.println("--- save ---");
        ObjectRecord r1 = new ObjectRecord("001");
        r1.setAttribute("nombre", "Juan");
        r1.setAttribute("edad", "20");
        fm.save(r1);
        System.out.println("Guardado: " + r1.getId());

        ObjectRecord r2 = new ObjectRecord("002");
        r2.setAttribute("nombre", "Ana");
        r2.setAttribute("ciudad", "Medellín");
        fm.save(r2);
        System.out.println("Guardado: " + r2.getId());

        ObjectRecord r3 = new ObjectRecord("003");
        r3.setAttribute("nombre", "Carlos");
        r3.setAttribute("carrera", "Sistemas");
        fm.save(r3);
        System.out.println("Guardado: " + r3.getId());

        // ---------- READ ALL ----------
        System.out.println("\n--- readAll ---");
        List<ObjectRecord> all = fm.readAll();
        for (ObjectRecord r : all) {
            System.out.println("  " + r);
        }

        // ---------- FIND BY ID ----------
        System.out.println("\n--- findById ---");
        Optional<ObjectRecord> found = fm.findById("002");
        found.ifPresentOrElse(
                r -> System.out.println("Encontrado: " + r),
                ()  -> System.out.println("No encontrado")
        );

        Optional<ObjectRecord> notFound = fm.findById("999");
        notFound.ifPresentOrElse(
                r -> System.out.println("Encontrado: " + r),
                ()  -> System.out.println("id='999' → No encontrado ✅")
        );

        // ---------- UPDATE ----------
        System.out.println("\n--- update ---");
        Map<String, String> cambios = new java.util.HashMap<>();
        cambios.put("edad", "21");
        cambios.put("ciudad", "Bogotá");
        boolean updated = fm.update("001", cambios);
        System.out.println("Actualizado '001': " + updated);
        fm.findById("001").ifPresent(r -> System.out.println("  Nuevo estado: " + r));

        // ---------- DELETE ----------
        System.out.println("\n--- delete ---");
        boolean deleted = fm.delete("003");
        System.out.println("Eliminado '003': " + deleted);

        boolean deletedAgain = fm.delete("003");
        System.out.println("Eliminar '003' de nuevo: " + deletedAgain + " (esperado: false)");

        // ---------- ESTADO FINAL ----------
        System.out.println("\n--- Estado final del archivo ---");
        fm.readAll().forEach(r -> System.out.println("  " + r));

        // ---------- DUPLICADO ----------
        System.out.println("\n--- Prueba de id duplicado ---");
        try {
            ObjectRecord dup = new ObjectRecord("001");
            dup.setAttribute("nombre", "Duplicado");
            fm.save(dup);
        } catch (IllegalStateException e) {
            System.out.println("Error esperado: " + e.getMessage() + " ✅");
        }

        System.out.println("\n✅ FileManager OK");
    }
}
