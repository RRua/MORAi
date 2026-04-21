package com.linkedlist.app

/**
 * LinkedList implementation with basic operations
 */
class LinkedList : Iterable<Node> {
    private var head: Node? = null

    /**
     * Insert a new element at the end of the list
     */
    fun insert(data: Int) {
        val newNode = Node(data)
        if (head == null) {
            head = newNode
            return
        }

        var current = head
        while (current?.next != null) {
            current = current.next
        }
        current?.next = newNode
    }

    /**
     * Insert a new element at the beginning of the list
     */
    fun insertAtHead(data: Int) {
        val newNode = Node(data)
        newNode.next = head
        head = newNode
    }

    /**
     * Delete the first occurrence of the specified value
     */
    fun delete(key: Int) {
        if (head == null) return

        if (head?.data == key) {
            head = head?.next
            return
        }

        var current = head
        while (current?.next != null && current.next?.data != key) {
            current = current.next
        }

        if (current?.next != null) {
            current.next = current.next?.next
        }
    }

    /**
     * Search for a value in the list
     */
    fun search(key: Int): Boolean {
        var current = head
        while (current != null) {
            if (current.data == key) return true
            current = current.next
        }
        return false
    }

    /**
     * Get the length of the list
     */
    fun length(): Int {
        var count = 0
        var current = head
        while (current != null) {
            count++
            current = current.next
        }
        return count
    }

    /**
     * Print the list to console
     */
    fun printList() {
        var current = head
        while (current != null) {
            print("${current.data} -> ")
            current = current.next
        }
        println("null")
    }

    /**
     * Traverse the list and return array of values
     */
    fun traverse(): IntArray {
        val len = length()
        val values = IntArray(len)
        var current = head
        var index = 0
        while (current != null) {
            values[index++] = current.data
            current = current.next
        }
        return values
    }

    /**
     * Traverse the list using iterator and return array of values
     */
    fun traverseWithIterator(): IntArray {
        val len = length()
        val result = IntArray(len)
        var i = 0

        val iterator: Iterator<Node> = LinkedListIterator(head)
        while (iterator.hasNext()) {
            val node = iterator.next()
            result[i++] = node.data
        }

        return result
    }

    override fun iterator(): Iterator<Node> {
        return LinkedListIterator(head)
    }
}
