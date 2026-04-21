package com.linkedlist.app

import android.util.Log
import org.apache.commons.lang3.StringUtils

/**
 * Main application class demonstrating LinkedList operations
 */
object LinkedListDemo {
    
    fun runDemo() {
        Log.d("LinkedListDemo", "LinkedList Demo Application")
        Log.d("LinkedListDemo", "===========================")
        
        val list = LinkedList()

        // Insert some elements
        list.insert(10)
        list.insert(20)
        list.insert(30)
        list.insertAtHead(5)

        Log.d("LinkedListDemo", "\nInitial List:")
        Log.d("LinkedListDemo", list.printList())

        Log.d("LinkedListDemo", "Length: ${list.length()}")

        // Search operations
        Log.d("LinkedListDemo", "Searching for 20: ${list.search(20)}")
        Log.d("LinkedListDemo", "Searching for 99: ${list.search(99)}")

        // Delete operation
        list.delete(20)
        Log.d("LinkedListDemo", "\nAfter deleting 20:")
        Log.d("LinkedListDemo", list.printList())

        // Traverse operations
        Log.d("LinkedListDemo", "\nValues from traverse():")
        val values1 = list.traverse()
        var values1String = ""
        for (value in values1) {
            values1String += "$value "
        }
        Log.d("LinkedListDemo", values1String)


        Log.d("LinkedListDemo", "\nValues from traverseWithIterator():")
        val values2 = list.traverseWithIterator()
        var values2String = ""
        for (value in values2) {
            values2String += "$value "
        }
        Log.d("LinkedListDemo", values2String)
        
        // Demonstrate external dependency usage
        demonstrateStringUtils()
        
        Log.d("LinkedListDemo", "\nDemo completed successfully!")
    }
    
    /**
     * Demonstrates usage of external dependency (Apache Commons Lang)
     */
    fun demonstrateStringUtils() {
        Log.d("LinkedListDemo", "\n=== External Dependency Demo ===")
        
        val text = "hello world"
        val capitalized = StringUtils.capitalize(text)
        val reversed = StringUtils.reverse(text)
        
        Log.d("LinkedListDemo", "Original: $text")
        Log.d("LinkedListDemo", "Capitalized: $capitalized")
        Log.d("LinkedListDemo", "Reversed: $reversed")
        Log.d("LinkedListDemo", "Is blank: ${StringUtils.isBlank("")}")
    }
}
