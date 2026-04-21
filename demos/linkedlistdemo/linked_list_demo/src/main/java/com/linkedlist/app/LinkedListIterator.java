package com.linkedlist.app;

import java.util.Iterator;

/**
 * Iterator implementation for the LinkedList
 */
public class LinkedListIterator implements Iterator<Node> {
    private Node current;

    public LinkedListIterator(Node start) {
        this.current = start;
    }

    @Override
    public boolean hasNext() {
        return current != null;
    }

    @Override
    public Node next() {
        Node temp = current;
        current = current.next;
        return temp;
    }
}
