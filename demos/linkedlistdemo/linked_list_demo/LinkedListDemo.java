import java.util.Iterator;

// Node class
class Node {
    int data;
    Node next;

    Node(int data) { // impure
        this.data = data;
        this.next = null;
    }
}

class LinkedListIterator implements Iterator<Node> {
    private Node current;

    LinkedListIterator(Node start) { // impure
        this.current = start;
        // Modifies only internal state
    }

    @Override
    public boolean hasNext() { // pure
        return current != null;
    }

    @Override
    public Node next() { // impure
        Node temp = current;
        current = current.next; // modifies internal state (advances iterator)
        return temp;
    }
}

// LinkedList class
class LinkedList implements Iterable<Node> {
    private Node head;

    // Insert at the end
    public void insert(int data) { // impure
        Node newNode = new Node(data);
        if (head == null) {
            head = newNode;
            return;
        }

        Node current = head;
        while (current.next != null) {
            current = current.next;
        }
        current.next = newNode;
    }

    // Insert at the beginning
    public void insertAtHead(int data) { // impure
        Node newNode = new Node(data);
        newNode.next = head;
        head = newNode;
    }

    // Delete a node by value
    public void delete(int key) { // impure
        if (head == null) return;

        if (head.data == key) {
            head = head.next;
            return;
        }

        Node current = head;
        while (current.next != null && current.next.data != key) {
            current = current.next;
        }

        if (current.next != null) {
            current.next = current.next.next;
        }
    }

    // Search for a node
    public boolean search(int key) { // pure
        Node current = head;
        while (current != null) {
            if (current.data == key) return true;
            current = current.next;
        }
        return false;
    }

    // Get length of the list
    public int length() { // pure
        int count = 0;
        Node current = head;
        while (current != null) {
            count++;
            current = current.next;
        }
        return count;
    }

    // Traverse and print the list
    public void printList() { // impure
        Node current = head;
        while (current != null) {
            System.out.print(current.data + " -> ");
            current = current.next;
        }
        System.out.println("null");
    }

    // Traverse the list and return array of values (pure)
    public int[] traverse() { // pure: no mutation, just reads
        int len = length(); // already a pure method
        int[] values = new int[len];
        Node current = head;
        int index = 0;
        while (current != null) {
            values[index++] = current.data;
            current = current.next;
        }
        return values;
    }

    public int foo(int x, int y, String z) {
        printList();
        return 0;
    }

    // Traverse using an iterator
    public int[] traverseWithIterator() { // pure
        int len = length(); // pure
        int[] result = new int[len];
        int i = 0;

        // Iterator<Node> iterator = iterator(); // Infer --purity gets wrong; classifies as impure
        Iterator<Node> iterator = new LinkedListIterator(head); // Infer --purity gets right; classifies as pure
        // Infer --impurity gets both approaches above right
        while (iterator.hasNext()) {
            Node node = iterator.next();
            result[i++] = node.data; // read-only access
        }

        return result;
    }

    // Required by Iterable interface
    @Override
    public Iterator<Node> iterator() { // pure (doesn't modify state but does read state)
        return new LinkedListIterator(head);
        // No side effects; returns new object each time
    }
}

public class LinkedListDemo {
    // Main method to test the list
    public static void main(String[] args) { // impure
        LinkedList list = new LinkedList();

        list.insert(10);
        list.insert(20);
        list.insert(30);
        list.insertAtHead(5);

        System.out.println("Initial List:");
        list.printList();

        System.out.println("Length: " + list.length());

        System.out.println("Searching for 20: " + list.search(20));
        System.out.println("Searching for 99: " + list.search(99));

        list.delete(20);
        System.out.println("After deleting 20:");
        list.printList();

        // Call and print result of traverse()
        System.out.println("Values from traverse():");
        int[] values1 = list.traverse();
        for (int val : values1) {
            System.out.print(val + " ");
        }
        System.out.println();

        // Call and print result of traverseWithIterator()
        System.out.println("Values from traverseWithIterator():");
        int[] values2 = list.traverseWithIterator();
        for (int val : values2) {
            System.out.print(val + " ");
        }
        System.out.println();
    }
}
