package com.linkedlist.app

import org.apache.commons.lang3.StringUtils

/**
 * Main application class demonstrating LinkedList operations
 */
object LinkedListDemo {
    
    @JvmStatic
    fun main(args: Array<String>) {
        println("LinkedList Demo Application")
        println("===========================")
        
        val list = LinkedList()

        // Insert some elements
        list.insert(10)
        list.insert(20)
        list.insert(30)
        list.insertAtHead(5)

        println("\nInitial List:")
        list.printList()

        println("Length: ${list.length()}")

        // Search operations
        println("Searching for 20: ${list.search(20)}")
        println("Searching for 99: ${list.search(99)}")

        // Delete operation
        list.delete(20)
        println("\nAfter deleting 20:")
        list.printList()

        // Traverse operations
        println("\nValues from traverse():")
        val values1 = list.traverse()
        for (value in values1) {
            print("$value ")
        }
        println()

        println("\nValues from traverseWithIterator():")
        val values2 = list.traverseWithIterator()
        for (value in values2) {
            print("$value ")
        }
        println()
        
        // Demonstrate external dependency usage
        demonstrateStringUtils()
        
        println("\nDemo completed successfully!")
    }
    
    /**
     * Demonstrates usage of external dependency (Apache Commons Lang)
     */
    @JvmStatic
    fun demonstrateStringUtils() {
        println("\n=== External Dependency Demo ===")
        
        val text = "hello world"
        val capitalized = StringUtils.capitalize(text)
        val reversed = StringUtils.reverse(text)
        
        println("Original: $text")
        println("Capitalized: $capitalized")
        println("Reversed: $reversed")
        println("Is blank: ${StringUtils.isBlank("")}")
    }
}
