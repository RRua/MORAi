package com.linkedlist.app;

import java.util.Iterator;

/**
 * Iterator implementation for the LinkedList
 */
public class JavaLinkedListIterator implements Iterator<JavaNode> {
    private JavaNode current;

    public JavaLinkedListIterator(JavaNode start) {
        this.current = start;
    }

    @Override
    public boolean hasNext() {
        return current != null;
    }

    @Override
    public JavaNode next() {
        JavaNode temp = current;
        current = current.next;
        return temp;
    }
}
