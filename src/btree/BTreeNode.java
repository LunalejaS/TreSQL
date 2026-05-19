package btree;

/**
 * Nodo del Árbol B de grado 4.
 *
 * ── Propiedades del nodo (grado 4 según Knuth: ORDER = máximo de hijos) ──────
 *
 *   ORDER        = 4   → número máximo de hijos por nodo
 *   MAX_KEYS     = 3   → número máximo de claves  (ORDER - 1)
 *   MIN_KEYS     = 1   → número mínimo de claves en nodo no-raíz (ceil(ORDER/2) - 1)
 *   MAX_CHILDREN = 4   → número máximo de hijos   (ORDER)
 *   MIN_CHILDREN = 2   → número mínimo de hijos en nodo interno no-raíz (ceil(ORDER/2))
 *
 * ── Estructura interna ────────────────────────────────────────────────────────
 *
 *   entries[]   → arreglo de KeyValue; cada entrada guarda clave + valor JSON.
 *   children[]  → arreglo de BTreeNode; los hijos de este nodo.
 *   numKeys     → cantidad actual de claves almacenadas en el nodo.
 *   isLeaf      → true si el nodo es hoja (no tiene hijos).
 *
 * ── Invariante de orden ───────────────────────────────────────────────────────
 *
 *   Para un nodo con k claves y k+1 hijos c[0]..c[k]:
 *     - Todas las claves de c[i] son menores que entries[i].key
 *     - Todas las claves de c[i+1] son mayores que entries[i].key
 */
public class BTreeNode {

    // =========================================================================
    // Constantes del grado 4
    // =========================================================================

    /** Orden del árbol B: número máximo de hijos por nodo. */
    public static final int ORDER = 4;

    /** Número máximo de claves por nodo = ORDER - 1 = 3. */
    public static final int MAX_KEYS = ORDER - 1;

    /**
     * Número mínimo de claves en un nodo que no sea la raíz.
     * = ceil(ORDER / 2) - 1 = 1
     */
    public static final int MIN_KEYS = (int) Math.ceil(ORDER / 2.0) - 1;

    /** Número máximo de hijos por nodo = ORDER = 4. */
    public static final int MAX_CHILDREN = ORDER;

    /**
     * Número mínimo de hijos en un nodo interno que no sea la raíz.
     * = ceil(ORDER / 2) = 2
     */
    public static final int MIN_CHILDREN = (int) Math.ceil(ORDER / 2.0);

    // =========================================================================
    // Atributos del nodo
    // =========================================================================

    /** Entradas (clave + valor) almacenadas en el nodo. */
    public KeyValue[] entries;

    /** Hijos del nodo. Un nodo hoja no usa este arreglo. */
    public BTreeNode[] children;

    /** Número actual de claves (entradas) en el nodo. */
    public int numKeys;

    /** Indica si el nodo es una hoja (sin hijos). */
    public boolean isLeaf;

    // =========================================================================
    // Constructor
    // =========================================================================

    /**
     * Crea un nodo nuevo vacío.
     *
     * @param isLeaf true si el nodo es hoja (no tiene hijos).
     */
    public BTreeNode(boolean isLeaf) {
        this.entries  = new KeyValue[MAX_KEYS];
        this.children = new BTreeNode[MAX_CHILDREN];
        this.numKeys  = 0;
        this.isLeaf   = isLeaf;
    }

    // =========================================================================
    // Consultas sobre el nodo
    // =========================================================================

    /**
     * Indica si el nodo ha alcanzado el máximo de claves permitidas.
     * Un nodo lleno debe ser partido antes de insertar una nueva clave.
     *
     * @return true si numKeys == MAX_KEYS.
     */
    public boolean isFull() {
        return numKeys == MAX_KEYS;
    }

    /**
     * Busca una clave dentro de este nodo (búsqueda lineal en las entradas).
     *
     * @param key Clave a buscar.
     * @return Índice dentro de entries[] si se encontró, -1 si no existe.
     */
    public int findKeyIndex(String key) {
        for (int i = 0; i < numKeys; i++) {
            if (entries[i].getKey().equals(key)) return i;
        }
        return -1;
    }

    /**
     * Calcula el índice del hijo al que debe descender una búsqueda o inserción.
     *
     * El índice i es el primer índice tal que key < entries[i].key.
     * Si key es mayor que todas las claves, retorna numKeys (último hijo).
     *
     * @param key Clave de referencia.
     * @return Índice del hijo apropiado (0 .. numKeys).
     */
    public int findChildIndex(String key) {
        int i = 0;
        while (i < numKeys && key.compareTo(entries[i].getKey()) > 0) {
            i++;
        }
        return i;
    }

    // =========================================================================
    // Representación textual
    // =========================================================================

    /**
     * Representación compacta del nodo para depuración.
     * Formato: [k1 | k2 | k3] o [k1] según el número de claves.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < numKeys; i++) {
            if (i > 0) sb.append(" | ");
            sb.append(entries[i].getKey());
        }
        return sb.append(isLeaf ? "] (hoja)" : "] (interno)").toString();
    }
}
