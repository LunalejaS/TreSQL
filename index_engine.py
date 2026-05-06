"""
Layer 4 — Index Engine
AVL self-balancing tree for primary key indexing.
Provides O(log n) insert, search, and delete operations.
Supports serialization to/from dict for disk persistence.
"""


class AVLNode:
    """A single node in the AVL tree."""

    def __init__(self, key, record_id):
        self.key = key
        self.record_id = record_id  # index into the records list on disk
        self.left = None
        self.right = None
        self.height = 1


class AVLTree:
    """AVL self-balancing binary search tree keyed on primary key values."""

    def __init__(self):
        self.root = None

    # ------------------------------------------------------------------
    # Internal helpers
    # ------------------------------------------------------------------

    def _height(self, node):
        return node.height if node else 0

    def _balance_factor(self, node):
        return self._height(node.left) - self._height(node.right)

    def _update_height(self, node):
        node.height = 1 + max(self._height(node.left), self._height(node.right))

    def _rotate_right(self, y):
        x = y.left
        t2 = x.right
        x.right = y
        y.left = t2
        self._update_height(y)
        self._update_height(x)
        return x

    def _rotate_left(self, x):
        y = x.right
        t2 = y.left
        y.left = x
        x.right = t2
        self._update_height(x)
        self._update_height(y)
        return y

    def _balance(self, node):
        self._update_height(node)
        bf = self._balance_factor(node)
        if bf > 1:
            if self._balance_factor(node.left) < 0:
                node.left = self._rotate_left(node.left)
            return self._rotate_right(node)
        if bf < -1:
            if self._balance_factor(node.right) > 0:
                node.right = self._rotate_right(node.right)
            return self._rotate_left(node)
        return node

    def _min_node(self, node):
        while node.left:
            node = node.left
        return node

    # ------------------------------------------------------------------
    # Public API
    # ------------------------------------------------------------------

    def insert(self, key, record_id):
        """Insert or update a key → record_id mapping."""
        self.root = self._insert(self.root, key, record_id)

    def _insert(self, node, key, record_id):
        if not node:
            return AVLNode(key, record_id)
        if key < node.key:
            node.left = self._insert(node.left, key, record_id)
        elif key > node.key:
            node.right = self._insert(node.right, key, record_id)
        else:
            node.record_id = record_id  # update existing mapping
            return node
        return self._balance(node)

    def search(self, key):
        """Return the record_id for *key*, or None if not found."""
        return self._search(self.root, key)

    def _search(self, node, key):
        if not node:
            return None
        if key == node.key:
            return node.record_id
        if key < node.key:
            return self._search(node.left, key)
        return self._search(node.right, key)

    def delete(self, key):
        """Remove the node with the given key from the tree."""
        self.root = self._delete(self.root, key)

    def _delete(self, node, key):
        if not node:
            return None
        if key < node.key:
            node.left = self._delete(node.left, key)
        elif key > node.key:
            node.right = self._delete(node.right, key)
        else:
            if not node.left:
                return node.right
            if not node.right:
                return node.left
            successor = self._min_node(node.right)
            node.key = successor.key
            node.record_id = successor.record_id
            node.right = self._delete(node.right, successor.key)
        return self._balance(node)

    def inorder(self):
        """Return list of (key, record_id) pairs in sorted order."""
        result = []
        self._inorder(self.root, result)
        return result

    def _inorder(self, node, result):
        if node:
            self._inorder(node.left, result)
            result.append((node.key, node.record_id))
            self._inorder(node.right, result)

    # ------------------------------------------------------------------
    # Serialization for disk persistence
    # ------------------------------------------------------------------

    def to_dict(self):
        """Serialize the tree to a nested dict (JSON-safe)."""
        return self._node_to_dict(self.root)

    def _node_to_dict(self, node):
        if not node:
            return None
        return {
            "key": node.key,
            "record_id": node.record_id,
            "height": node.height,
            "left": self._node_to_dict(node.left),
            "right": self._node_to_dict(node.right),
        }

    def from_dict(self, data):
        """Restore the tree from a previously serialized dict."""
        self.root = self._dict_to_node(data)

    def _dict_to_node(self, data):
        if not data:
            return None
        node = AVLNode(data["key"], data["record_id"])
        node.height = data["height"]
        node.left = self._dict_to_node(data["left"])
        node.right = self._dict_to_node(data["right"])
        return node
