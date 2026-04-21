package com.linkedlist.app;

import org.apache.commons.lang3.StringUtils;

/**
 * Main application class demonstrating LinkedList operations
 */
public class LinkedListDemo {
    
    public static void main(String[] args) {
        System.out.println("LinkedList Demo Application");
        System.out.println("===========================");
        
        LinkedList list = new LinkedList();

        // Insert some elements
        list.insert(10);
        list.insert(20);
        list.insert(30);
        list.insertAtHead(5);

        System.out.println("\nInitial List:");
        list.printList();

        System.out.println("Length: " + list.length());

        // Search operations
        System.out.println("Searching for 20: " + list.search(20));
        System.out.println("Searching for 99: " + list.search(99));

        // Delete operation
        list.delete(20);
        System.out.println("\nAfter deleting 20:");
        list.printList();

        // Traverse operations
        System.out.println("\nValues from traverse():");
        int[] values1 = list.traverse();
        for (int val : values1) {
            System.out.print(val + " ");
        }
        System.out.println();

        System.out.println("\nValues from traverseWithIterator():");
        int[] values2 = list.traverseWithIterator();
        for (int val : values2) {
            System.out.print(val + " ");
        }
        System.out.println();
        
        // Demonstrate external dependency usage
        demonstrateStringUtils();
        
        System.out.println("\nDemo completed successfully!");
    }
    
    /**
     * Demonstrates usage of external dependency (Apache Commons Lang)
     */
    public static void demonstrateStringUtils() {
        System.out.println("\n=== External Dependency Demo ===");
        
        String text = "hello world";
        String capitalized = StringUtils.capitalize(text);
        String reversed = StringUtils.reverse(text);
        
        System.out.println("Original: " + text);
        System.out.println("Capitalized: " + capitalized);
        System.out.println("Reversed: " + reversed);
        System.out.println("Is blank: " + StringUtils.isBlank(""));
    }
}
