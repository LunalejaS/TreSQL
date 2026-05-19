package btree;

import java.util.ArrayList;
import java.util.List;

/**
 * Árbol B de grado 4 (ORDER = 4).
 *
 * ── Resumen del comportamiento ────────────────────────────────────────────────
 *
 *   Inserción:
 *     Si la raíz está llena → se parte primero y el árbol crece en altura.
 *     Se desciende de forma "proactiva": cualquier nodo lleno se parte antes
 *     de bajar a él, evitando retroceder en la recursión.
 *
 *   Búsqueda:
 *     Se comparan las claves del nodo actual y se desciende al hijo apropiado
 *     hasta encontrar la clave o llegar a una hoja.
 *
 *   Eliminación:
 *     Caso 1 → clave en hoja: se elimina directamente.
 *     Caso 2 → clave en nodo interno:
 *       2a. Predecesor inorden (hijo izquierdo con > MIN_KEYS) → reemplazar y borrar.
 *       2b. Sucesor inorden   (hijo derecho con > MIN_KEYS)    → reemplazar y borrar.
 *       2c. Ambos hijos con MIN_KEYS → fusionar y borrar de la fusión.
 *     Caso 3 → clave no en el nodo actual: asegurar que el hijo tenga > MIN_KEYS
 *              antes de descender (tomar prestado o fusionar).
 *
 *   Complejidad garantizada: O(log n) para búsqueda, inserción y eliminación.
 *
 * ── Operaciones públicas ──────────────────────────────────────────────────────
 *
 *   insert(key, value)         → inserta un nuevo par clave-valor.
 *   search(key)  → String      → retorna el valor JSON o null.
 *   update(key, newValue)      → actualiza el valor de una clave existente.
 *   delete(key)                → elimina la clave y su valor.
 *   contains(key) → boolean    → comprueba si la clave existe.
 *   traverse()    → List       → todas las entradas en orden ascendente.
 *   size()        → int        → número total de claves.
 *   printTree()                → imprime el árbol visualmente por niveles.
 */
public class BTree {

    // =========================================================================
    // Atributos
    // =========================================================================

    /** Raíz del árbol. Siempre existe; al inicio es una hoja vacía. */
    private BTreeNode root;

    /** Contador de claves totales almacenadas en el árbol. */
    private int totalKeys;

    // =========================================================================
    // Constructor
    // =========================================================================

    /**
     * Crea un árbol B vacío de grado 4.
     * La raíz inicial es una hoja vacía, sin claves ni hijos.
     */
    public BTree() {
        this.root      = new BTreeNode(true);
        this.totalKeys = 0;
    }

    // =========================================================================
    // BÚSQUEDA
    // =========================================================================

    /**
     * Busca el valor JSON asociado a la clave dada.
     *
     * Complejidad: O(log n).
     *
     * @param key Clave a buscar.
     * @return Valor JSON del objeto, o null si la clave no existe.
     */
    public String search(String key) {
        KeyValue result = searchRecursive(root, key);
        return (result != null) ? result.getValue() : null;
    }

    /**
     * Descenso recursivo para buscar una clave.
     * En cada nodo: busca en las entradas actuales, o desciende al hijo adecuado.
     */
    private KeyValue searchRecursive(BTreeNode node, String key) {
        if (node == null) return null;

        // Buscar en las entradas del nodo actual
        int idx = node.findKeyIndex(key);
        if (idx != -1) return node.entries[idx];

        // Si es hoja, la clave no existe
        if (node.isLeaf) return null;

        // Descender al hijo correspondiente
        return searchRecursive(node.children[node.findChildIndex(key)], key);
    }

    /**
     * Indica si una clave existe en el árbol.
     *
     * @param key Clave a verificar.
     * @return true si existe, false si no.
     */
    public boolean contains(String key) {
        return search(key) != null;
    }

    // =========================================================================
    // INSERCIÓN
    // =========================================================================

    /**
     * Inserta un nuevo par clave-valor en el árbol.
     *
     * Si la raíz está llena, se parte antes de insertar:
     * se crea una nueva raíz vacía que apunta a la raíz actual como hijo,
     * y se parte ese hijo. El árbol crece en altura desde la raíz.
     *
     * Complejidad: O(log n).
     *
     * @param key   Clave única del objeto.
     * @param value Valor JSON asociado.
     * @throws IllegalArgumentException si la clave ya existe en el árbol.
     */
    public void insert(String key, String value) {
        if (contains(key)) {
            throw new IllegalArgumentException(
                "La clave '" + key + "' ya existe. Use update() para modificar su valor.");
        }

        BTreeNode r = root;

        if (r.isFull()) {
            // La raíz está llena: crear nueva raíz y partir la anterior
            BTreeNode newRoot = new BTreeNode(false); // nueva raíz no es hoja
            newRoot.children[0] = r;                  // la vieja raíz es primer hijo
            splitChild(newRoot, 0, r);                 // partir la vieja raíz
            root = newRoot;
        }

        // Insertar en el subárbol que no está lleno
        insertNonFull(root, key, value);
        totalKeys++;
    }

    /**
     * Inserta en un nodo que garantizadamente NO está lleno.
     *
     * Estrategia proactiva: antes de descender a un hijo, si está lleno,
     * se parte para evitar tener que retroceder.
     *
     * @param node  Nodo actual (no lleno).
     * @param key   Clave a insertar.
     * @param value Valor JSON a insertar.
     */
    private void insertNonFull(BTreeNode node, String key, String value) {
        int i = node.numKeys - 1;

        if (node.isLeaf) {
            // Desplazar entradas mayores a la derecha para abrir espacio
            while (i >= 0 && key.compareTo(node.entries[i].getKey()) < 0) {
                node.entries[i + 1] = node.entries[i];
                i--;
            }
            // Insertar la nueva entrada
            node.entries[i + 1] = new KeyValue(key, value);
            node.numKeys++;

        } else {
            // Encontrar el hijo adecuado
            while (i >= 0 && key.compareTo(node.entries[i].getKey()) < 0) {
                i--;
            }
            i++; // índice del hijo al que descender

            // Si el hijo está lleno, partirlo antes de descender
            if (node.children[i].isFull()) {
                splitChild(node, i, node.children[i]);
                // Después del split, la clave mediana subió a node.entries[i].
                // Decidir a cuál de los dos hijos bajar.
                if (key.compareTo(node.entries[i].getKey()) > 0) {
                    i++;
                }
            }

            insertNonFull(node.children[i], key, value);
        }
    }

    /**
     * Parte el hijo {@code child} del nodo {@code parent} en la posición {@code childIndex}.
     *
     * El hijo {@code child} debe estar lleno (MAX_KEYS = 3 entradas).
     * Después de partir:
     *   - La entrada mediana (índice 1 de 0..2) sube al padre.
     *   - child conserva las entradas [0..median-1] → 1 entrada.
     *   - Un nuevo nodo hermano recibe las entradas [median+1..MAX_KEYS-1] → 1 entrada.
     *   - Los hijos de child se redistribuyen de la misma forma.
     *
     * Para ORDER=4: mediana = índice 1 (Math.ceil(ORDER/2) - 1 = 1).
     *
     * @param parent     Nodo padre (no lleno).
     * @param childIndex Índice de child dentro de parent.children[].
     * @param child      Nodo hijo lleno que se va a partir.
     */
    private void splitChild(BTreeNode parent, int childIndex, BTreeNode child) {
        // Índice de la entrada mediana dentro del hijo lleno
        int medianIndex = (int) Math.ceil(BTreeNode.ORDER / 2.0) - 1; // = 1 para ORDER=4

        // Crear el nuevo nodo hermano (parte derecha)
        BTreeNode sibling = new BTreeNode(child.isLeaf);

        // Número de entradas que van al hermano = entradas después de la mediana
        int siblingsKeyCount = child.numKeys - medianIndex - 1;
        sibling.numKeys = siblingsKeyCount;

        // Copiar entradas de la mitad derecha al hermano
        for (int j = 0; j < siblingsKeyCount; j++) {
            sibling.entries[j] = child.entries[medianIndex + 1 + j];
            child.entries[medianIndex + 1 + j] = null; // limpiar referencia
        }

        // Si el hijo no es hoja, copiar también los punteros a hijos
        if (!child.isLeaf) {
            for (int j = 0; j <= siblingsKeyCount; j++) {
                sibling.children[j] = child.children[medianIndex + 1 + j];
                child.children[medianIndex + 1 + j] = null; // limpiar referencia
            }
        }

        // El hijo original queda solo con las entradas de la mitad izquierda
        KeyValue medianEntry = child.entries[medianIndex];
        child.entries[medianIndex] = null; // limpiar la mediana del hijo
        child.numKeys = medianIndex;       // = 1 para ORDER=4

        // Hacer espacio en el padre: desplazar hijos y entradas a la derecha
        for (int j = parent.numKeys; j > childIndex; j--) {
            parent.children[j + 1] = parent.children[j];
        }
        parent.children[childIndex + 1] = sibling; // insertar hermano

        for (int j = parent.numKeys - 1; j >= childIndex; j--) {
            parent.entries[j + 1] = parent.entries[j];
        }

        // Subir la entrada mediana al padre
        parent.entries[childIndex] = medianEntry;
        parent.numKeys++;
    }

    // =========================================================================
    // ACTUALIZACIÓN
    // =========================================================================

    /**
     * Actualiza el valor JSON de una clave existente.
     *
     * La clave permanece inmutable; solo su valor cambia.
     * La estructura del árbol no se modifica.
     *
     * Complejidad: O(log n).
     *
     * @param key      Clave del objeto a actualizar.
     * @param newValue Nuevo valor JSON.
     * @throws IllegalArgumentException si la clave no existe.
     */
    public void update(String key, String newValue) {
        if (!updateRecursive(root, key, newValue)) {
            throw new IllegalArgumentException(
                "La clave '" + key + "' no existe. Use insert() para agregar nuevas claves.");
        }
    }

    /**
     * Descenso recursivo para actualizar el valor de una clave.
     *
     * @return true si la clave fue encontrada y actualizada.
     */
    private boolean updateRecursive(BTreeNode node, String key, String newValue) {
        if (node == null) return false;

        int idx = node.findKeyIndex(key);
        if (idx != -1) {
            node.entries[idx].setValue(newValue);
            return true;
        }

        if (node.isLeaf) return false;

        return updateRecursive(node.children[node.findChildIndex(key)], key, newValue);
    }

    // =========================================================================
    // ELIMINACIÓN
    // =========================================================================

    /**
     * Elimina una clave y su valor del árbol.
     *
     * Si después de eliminar la raíz queda sin claves (por una fusión que
     * propagó hasta arriba), la raíz baja un nivel.
     *
     * Complejidad: O(log n).
     *
     * @param key Clave a eliminar.
     * @throws IllegalArgumentException si la clave no existe.
     */
    public void delete(String key) {
        if (!contains(key)) {
            throw new IllegalArgumentException(
                "La clave '" + key + "' no existe en el árbol.");
        }
        deleteRecursive(root, key);
        totalKeys--;

        // Si la raíz quedó vacía por una fusión que llegó hasta arriba,
        // su único hijo pasa a ser la nueva raíz. El árbol reduce su altura.
        if (root.numKeys == 0 && !root.isLeaf) {
            root = root.children[0];
        }
    }

    /**
     * Descenso recursivo de eliminación.
     *
     * En cada nivel se garantiza que el nodo actual tiene al menos MIN_KEYS+1
     * claves (excepto la raíz), para poder eliminar sin violar las propiedades
     * del árbol B. Esta garantía se cumple "rellenando" el hijo antes de bajar.
     */
    private void deleteRecursive(BTreeNode node, String key) {
        int idx = findFirstGeq(node, key); // primer i tal que entries[i].key >= key

        boolean keyInThisNode = (idx < node.numKeys)
                                && node.entries[idx].getKey().equals(key);

        if (keyInThisNode) {
            if (node.isLeaf) {
                // ── Caso 1: la clave está en una hoja → eliminar directamente ──
                removeFromLeaf(node, idx);

            } else {
                // ── Caso 2: la clave está en un nodo interno ──────────────────
                deleteFromInternalNode(node, idx, key);
            }

        } else {
            // ── Caso 3: la clave no está en este nodo ─────────────────────────
            if (node.isLeaf) {
                // No debería ocurrir (contains() garantiza que existe)
                return;
            }

            // El hijo apropiado es children[idx]
            boolean isLastChild = (idx == node.numKeys);
            BTreeNode child = node.children[idx];

            // Si el hijo tiene solo MIN_KEYS, rellenarlo antes de bajar
            if (child.numKeys == BTreeNode.MIN_KEYS) {
                fillChild(node, idx);

                // fillChild puede haber fusionado o redistribuido nodos.
                // Recalcular el hijo al que descender.
                if (isLastChild && idx > node.numKeys) {
                    deleteRecursive(node.children[idx - 1], key);
                } else {
                    deleteRecursive(node.children[idx], key);
                }
            } else {
                deleteRecursive(child, key);
            }
        }
    }

    /**
     * Retorna el primer índice i tal que entries[i].key >= key,
     * o node.numKeys si key es mayor que todas las claves del nodo.
     */
    private int findFirstGeq(BTreeNode node, String key) {
        int i = 0;
        while (i < node.numKeys && key.compareTo(node.entries[i].getKey()) > 0) {
            i++;
        }
        return i;
    }

    /**
     * Caso 1: elimina la entrada en el índice {@code idx} de un nodo hoja.
     * Desplaza las entradas posteriores a la izquierda.
     */
    private void removeFromLeaf(BTreeNode node, int idx) {
        for (int i = idx + 1; i < node.numKeys; i++) {
            node.entries[i - 1] = node.entries[i];
        }
        node.entries[node.numKeys - 1] = null;
        node.numKeys--;
    }

    /**
     * Caso 2: elimina la entrada en el índice {@code idx} de un nodo interno.
     *
     * 2a. Si el hijo izquierdo (children[idx]) tiene más de MIN_KEYS:
     *       tomar el predecesor inorden (máximo del subárbol izquierdo),
     *       reemplazar la entrada, y borrar el predecesor en el subárbol.
     *
     * 2b. Si el hijo derecho (children[idx+1]) tiene más de MIN_KEYS:
     *       tomar el sucesor inorden (mínimo del subárbol derecho),
     *       reemplazar la entrada, y borrar el sucesor en el subárbol.
     *
     * 2c. Ambos hijos tienen exactamente MIN_KEYS:
     *       fusionar entries[idx], hijo derecho en hijo izquierdo,
     *       y borrar la clave del nodo fusionado.
     */
    private void deleteFromInternalNode(BTreeNode node, int idx, String key) {
        BTreeNode leftChild  = node.children[idx];
        BTreeNode rightChild = node.children[idx + 1];

        if (leftChild.numKeys > BTreeNode.MIN_KEYS) {
            // Caso 2a: predecesor inorden
            KeyValue predecessor = getPredecessor(leftChild);
            node.entries[idx] = new KeyValue(predecessor.getKey(), predecessor.getValue());
            deleteRecursive(leftChild, predecessor.getKey());

        } else if (rightChild.numKeys > BTreeNode.MIN_KEYS) {
            // Caso 2b: sucesor inorden
            KeyValue successor = getSuccessor(rightChild);
            node.entries[idx] = new KeyValue(successor.getKey(), successor.getValue());
            deleteRecursive(rightChild, successor.getKey());

        } else {
            // Caso 2c: fusionar hijo izquierdo + entries[idx] + hijo derecho
            // La clave que queremos borrar baja al nodo fusionado (leftChild).
            // Después del merge, ese nodo fusionado queda en children[idx].
            merge(node, idx);
            deleteRecursive(node.children[idx], key);
        }
    }

    /**
     * Retorna la entrada del predecesor inorden del subárbol dado.
     * El predecesor inorden es la entrada más a la derecha de la hoja más derecha.
     */
    private KeyValue getPredecessor(BTreeNode node) {
        // Descender siempre por el hijo más a la derecha
        while (!node.isLeaf) {
            node = node.children[node.numKeys];
        }
        return node.entries[node.numKeys - 1];
    }

    /**
     * Retorna la entrada del sucesor inorden del subárbol dado.
     * El sucesor inorden es la entrada más a la izquierda de la hoja más izquierda.
     */
    private KeyValue getSuccessor(BTreeNode node) {
        // Descender siempre por el hijo más a la izquierda
        while (!node.isLeaf) {
            node = node.children[0];
        }
        return node.entries[0];
    }

    /**
     * Garantiza que el hijo en la posición {@code childIdx} tenga al menos
     * MIN_KEYS + 1 claves antes de descender a él.
     *
     * Estrategias (en orden de preferencia):
     *   a) Tomar prestado del hermano izquierdo  (si tiene > MIN_KEYS).
     *   b) Tomar prestado del hermano derecho    (si tiene > MIN_KEYS).
     *   c) Fusionar con un hermano.
     */
    private void fillChild(BTreeNode parent, int childIdx) {
        boolean leftHasSurplus  = childIdx > 0
                && parent.children[childIdx - 1].numKeys > BTreeNode.MIN_KEYS;
        boolean rightHasSurplus = childIdx < parent.numKeys
                && parent.children[childIdx + 1].numKeys > BTreeNode.MIN_KEYS;

        if (leftHasSurplus) {
            borrowFromLeft(parent, childIdx);
        } else if (rightHasSurplus) {
            borrowFromRight(parent, childIdx);
        } else {
            // Fusionar con el hermano disponible
            if (childIdx < parent.numKeys) {
                merge(parent, childIdx);        // fusionar con hermano derecho
            } else {
                merge(parent, childIdx - 1);    // fusionar con hermano izquierdo
            }
        }
    }

    /**
     * El hijo en {@code childIdx} toma prestada la última entrada del hermano izquierdo.
     *
     * La entrada del padre desciende al inicio del hijo.
     * La última entrada del hermano izquierdo sube al padre.
     */
    private void borrowFromLeft(BTreeNode parent, int childIdx) {
        BTreeNode child   = parent.children[childIdx];
        BTreeNode sibling = parent.children[childIdx - 1]; // hermano izquierdo

        // Desplazar todas las entradas del hijo a la derecha para abrir el índice 0
        for (int i = child.numKeys - 1; i >= 0; i--) {
            child.entries[i + 1] = child.entries[i];
        }
        // Si no es hoja, desplazar también los punteros a hijos
        if (!child.isLeaf) {
            for (int i = child.numKeys; i >= 0; i--) {
                child.children[i + 1] = child.children[i];
            }
        }

        // La entrada del padre (separador entre hermano y child) baja al inicio del hijo
        child.entries[0] = parent.entries[childIdx - 1];
        // El último hijo del hermano pasa a ser el primer hijo del child
        if (!child.isLeaf) {
            child.children[0] = sibling.children[sibling.numKeys];
        }
        child.numKeys++;

        // La última entrada del hermano sube al padre
        parent.entries[childIdx - 1] = sibling.entries[sibling.numKeys - 1];
        sibling.entries[sibling.numKeys - 1] = null;
        sibling.numKeys--;
    }

    /**
     * El hijo en {@code childIdx} toma prestada la primera entrada del hermano derecho.
     *
     * La entrada del padre desciende al final del hijo.
     * La primera entrada del hermano derecho sube al padre.
     */
    private void borrowFromRight(BTreeNode parent, int childIdx) {
        BTreeNode child   = parent.children[childIdx];
        BTreeNode sibling = parent.children[childIdx + 1]; // hermano derecho

        // La entrada separadora del padre baja al final del hijo
        child.entries[child.numKeys] = parent.entries[childIdx];
        // El primer hijo del hermano pasa a ser el último hijo del child
        if (!child.isLeaf) {
            child.children[child.numKeys + 1] = sibling.children[0];
        }
        child.numKeys++;

        // La primera entrada del hermano sube al padre
        parent.entries[childIdx] = sibling.entries[0];

        // Desplazar entradas del hermano a la izquierda
        for (int i = 1; i < sibling.numKeys; i++) {
            sibling.entries[i - 1] = sibling.entries[i];
        }
        if (!sibling.isLeaf) {
            for (int i = 1; i <= sibling.numKeys; i++) {
                sibling.children[i - 1] = sibling.children[i];
            }
        }
        sibling.entries[sibling.numKeys - 1] = null;
        sibling.numKeys--;
    }

    /**
     * Fusiona el hijo en {@code childIdx + 1} dentro del hijo en {@code childIdx},
     * bajando la entrada separadora del padre ({@code parent.entries[childIdx]}).
     *
     * Después de la fusión:
     *   - El hijo izquierdo absorbe: la entrada separadora del padre + todas las
     *     entradas e hijos del hijo derecho.
     *   - El hijo derecho desaparece.
     *   - El padre pierde una entrada y un puntero a hijo.
     */
    private void merge(BTreeNode parent, int childIdx) {
        BTreeNode leftChild  = parent.children[childIdx];
        BTreeNode rightChild = parent.children[childIdx + 1];

        // --- Guardar cuántas claves tenía leftChild antes de modificarlo ---
        int leftOriginalKeys = leftChild.numKeys;

        // Bajar la entrada separadora del padre al final del hijo izquierdo
        leftChild.entries[leftOriginalKeys] = parent.entries[childIdx];
        leftChild.numKeys++;

        // Copiar todas las entradas del hijo derecho al izquierdo
        for (int i = 0; i < rightChild.numKeys; i++) {
            leftChild.entries[leftOriginalKeys + 1 + i] = rightChild.entries[i];
            leftChild.numKeys++;
        }

        // Copiar los punteros a hijos del hijo derecho (si no son hojas)
        if (!rightChild.isLeaf) {
            for (int i = 0; i <= rightChild.numKeys; i++) {
                leftChild.children[leftOriginalKeys + 1 + i] = rightChild.children[i];
            }
        }

        // Eliminar la entrada separadora del padre y el puntero al hijo derecho.
        // Desplazar entradas del padre a la izquierda desde childIdx+1.
        for (int i = childIdx + 1; i < parent.numKeys; i++) {
            parent.entries[i - 1] = parent.entries[i];
        }
        parent.entries[parent.numKeys - 1] = null;

        // Desplazar punteros a hijos del padre a la izquierda desde childIdx+2.
        for (int i = childIdx + 2; i <= parent.numKeys; i++) {
            parent.children[i - 1] = parent.children[i];
        }
        parent.children[parent.numKeys] = null;
        parent.numKeys--;
    }

    // =========================================================================
    // RECORRIDO INORDEN
    // =========================================================================

    /**
     * Retorna todas las entradas del árbol en orden ascendente de clave
     * (recorrido inorden del árbol B).
     *
     * Complejidad: O(n).
     *
     * @return Lista de KeyValue ordenada por clave.
     */
    public List<KeyValue> traverse() {
        List<KeyValue> result = new ArrayList<>();
        inorderTraversal(root, result);
        return result;
    }

    /**
     * Recorrido inorden recursivo.
     *
     * Para un nodo con k claves y k+1 hijos:
     *   recorrer c[0], visitar entries[0], recorrer c[1], visitar entries[1], ...
     *   recorrer c[k-1], visitar entries[k-1], recorrer c[k].
     */
    private void inorderTraversal(BTreeNode node, List<KeyValue> result) {
        if (node == null) return;

        for (int i = 0; i < node.numKeys; i++) {
            if (!node.isLeaf) {
                inorderTraversal(node.children[i], result);
            }
            result.add(node.entries[i]);
        }
        // Recorrer el último hijo (si no es hoja)
        if (!node.isLeaf) {
            inorderTraversal(node.children[node.numKeys], result);
        }
    }

    // =========================================================================
    // UTILIDADES Y DEPURACIÓN
    // =========================================================================

    /**
     * Retorna el número total de claves almacenadas en el árbol.
     */
    public int size() {
        return totalKeys;
    }

    /**
     * Alias de {@link #size()} — retorna el número total de claves.
     * Expuesto para compatibilidad con DatabaseManager.
     */
    public int getTotalKeys() {
        return totalKeys;
    }


    /**
     * Retorna true si el árbol no contiene ninguna clave.
     */
    public boolean isEmpty() {
        return totalKeys == 0;
    }

    /**
     * Imprime una representación visual del árbol por niveles.
     *
     * Formato de cada nodo: [k1 | k2 | k3] (hoja) o [k1 | k2 | k3] (interno)
     * Los hijos se indentan con └── o ├── para mostrar la jerarquía.
     */
    public void printTree() {
        if (isEmpty()) {
            System.out.println("(árbol B vacío)");
            return;
        }
        System.out.println("Árbol B — ORDER=" + BTreeNode.ORDER
                + " | MAX_KEYS=" + BTreeNode.MAX_KEYS
                + " | Total claves: " + totalKeys);
        printTreeRecursive(root, "", true);
    }

    /**
     * Impresión recursiva del árbol usando prefijos de árbol ASCII.
     */
    private void printTreeRecursive(BTreeNode node, String prefix, boolean isLast) {
        if (node == null) return;

        System.out.println(prefix + (isLast ? "└── " : "├── ") + node);

        if (!node.isLeaf) {
            String childPrefix = prefix + (isLast ? "    " : "│   ");
            for (int i = 0; i <= node.numKeys; i++) {
                boolean lastChild = (i == node.numKeys);
                printTreeRecursive(node.children[i], childPrefix, lastChild);
            }
        }
    }

    // =========================================================================
    // MAIN TEMPORAL DE PRUEBA
    // =========================================================================

    /**
     * Main temporal para verificar el funcionamiento del árbol B.
     *
     * Prueba las siguientes operaciones:
     *   1. Inserción de 10 registros con claves no ordenadas.
     *   2. Visualización del árbol con printTree().
     *   3. Búsqueda de claves existentes y no existentes.
     *   4. Actualización de un valor.
     *   5. Eliminación de claves (hoja, interno, con fusión).
     *   6. Recorrido inorden al final.
     */
    public static void main(String[] args) {
        BTree tree = new BTree();

        System.out.println("═══════════════════════════════════════════════════");
        System.out.println("  PRUEBA — Árbol B Grado 4 (ORDER=4, MAX_KEYS=3)");
        System.out.println("═══════════════════════════════════════════════════\n");

        // ── 1. INSERCIÓN ──────────────────────────────────────────────────────
        System.out.println("── INSERCIÓN (10 registros) ──");
        // NOTA: Las claves se comparan lexicograficamente (String.compareTo).
        //       Para que el orden numerico coincida con el lexico, se usa
        //       zero-padding de 3 digitos: "001", "002", ..., "010".
        String[][] registros = {
            {"005", "{\"id\":\"005\",\"nombre\":\"Elena Vargas\",\"carrera\":\"Ingenieria\"}"},
            {"003", "{\"id\":\"003\",\"nombre\":\"Carlos Ruiz\",\"carrera\":\"Sistemas\"}"},
            {"007", "{\"id\":\"007\",\"nombre\":\"Ana Martinez\",\"carrera\":\"Matematicas\"}"},
            {"001", "{\"id\":\"001\",\"nombre\":\"Diego Lopez\",\"carrera\":\"Ingenieria\"}"},
            {"009", "{\"id\":\"009\",\"nombre\":\"Sara Jimenez\",\"carrera\":\"Fisica\"}"},
            {"002", "{\"id\":\"002\",\"nombre\":\"Luis Herrera\",\"carrera\":\"Sistemas\"}"},
            {"006", "{\"id\":\"006\",\"nombre\":\"Paula Mora\",\"carrera\":\"Matematicas\"}"},
            {"004", "{\"id\":\"004\",\"nombre\":\"Andres Castro\",\"carrera\":\"Ingenieria\"}"},
            {"008", "{\"id\":\"008\",\"nombre\":\"Valentina Rios\",\"carrera\":\"Sistemas\"}"},
            {"010", "{\"id\":\"010\",\"nombre\":\"Mateo Gomez\",\"carrera\":\"Fisica\"}"},
        };

        for (String[] r : registros) {
            tree.insert(r[0], r[1]);
            System.out.println("  Insertado: id=" + r[0]);
        }

        System.out.println("\nÁrbol después de todas las inserciones:");
        tree.printTree();

        // ── 2. BÚSQUEDA ───────────────────────────────────────────────────────
        System.out.println("\n── BÚSQUEDA ──");
        String[] clavesBuscar = {"003", "007", "010", "099"};
        for (String key : clavesBuscar) {
            String val = tree.search(key);
            if (val != null) {
                System.out.println("  [ENCONTRADO] id=" + key + " -> " + val);
            } else {
                System.out.println("  [NO ENCONTRADO] id=" + key);
            }
        }

        // ── 3. ACTUALIZACIÓN ──────────────────────────────────────────────────
        System.out.println("\n── ACTUALIZACIÓN ──");
        System.out.println("  Antes : " + tree.search("003"));
        tree.update("003", "{\"id\":\"003\",\"nombre\":\"Carlos Ruiz\",\"carrera\":\"Sistemas\",\"nota\":\"Graduando\"}");
        System.out.println("  Despues: " + tree.search("003"));

        // ── 4. ELIMINACIÓN ────────────────────────────────────────────────────
        System.out.println("\n── ELIMINACIÓN ──");
        String[] clavesEliminar = {"005", "009", "001"};
        for (String key : clavesEliminar) {
            System.out.println("  Eliminando id=" + key + " ...");
            tree.delete(key);
            System.out.println("  Arbol tras eliminar " + key + ":");
            tree.printTree();
        }

        // ── 5. RECORRIDO INORDEN ──────────────────────────────────────────────
        System.out.println("\n── RECORRIDO INORDEN (orden ascendente de clave) ──");
        List<KeyValue> inorden = tree.traverse();
        for (KeyValue kv : inorden) {
            System.out.println("  id=" + kv.getKey() + " → " + kv.getValue());
        }

        System.out.println("\nTotal claves en el árbol: " + tree.size());
        System.out.println("═══════════════════════════════════════════════════");

        // ── 6. PRUEBA DE CLAVE DUPLICADA ──────────────────────────────────────
        System.out.println("\n── PRUEBA ERROR: clave duplicada ──");
        try {
            tree.insert("002", "{\"id\":\"002\",\"duplicado\":\"true\"}");
        } catch (IllegalArgumentException e) {
            System.out.println("  Excepcion esperada: " + e.getMessage());
        }

        // ── 7. PRUEBA DE CLAVE INEXISTENTE EN DELETE ──────────────────────────
        System.out.println("\n── PRUEBA ERROR: eliminar clave inexistente ──");
        try {
            tree.delete("099");
        } catch (IllegalArgumentException e) {
            System.out.println("  Excepcion esperada: " + e.getMessage());
        }
    }
}
