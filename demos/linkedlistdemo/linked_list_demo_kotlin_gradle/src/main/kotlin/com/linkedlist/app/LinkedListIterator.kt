package com.linkedlist.app

/**
 * Iterator implementation for the LinkedList
 */
class LinkedListIterator(private var current: Node?) : Iterator<Node> {
    
    override fun hasNext(): Boolean {
        return current != null
    }

    override fun next(): Node {
        val temp = current ?: throw NoSuchElementException()
        current = current?.next
        return temp
    }
}
