package com.linkedlist.app;

import dev.memoize.annotations.CacheInvalidate;
import dev.memoize.annotations.Memoize;
import java.util.Iterator;

/**
 * LinkedList implementation with basic operations
 */
public class JavaLinkedList implements Iterable<JavaNode> {
    private JavaNode head;

    /**
     * Insert a new element at the end of the list
     */
    @CacheInvalidate
    public void insert(int data) {
        JavaNode newNode = new JavaNode(data);
        if (head == null) {
            head = newNode;
            return;
        }

        JavaNode current = head;
        while (current.next != null) {
            current = current.next;
        }
        current.next = newNode;
    }

    /**
     * Insert a new element at the beginning of the list
     */
    @CacheInvalidate
    public void insertAtHead(int data) {
        JavaNode newNode = new JavaNode(data);
        newNode.next = head;
        head = newNode;
    }

    /**
     * Delete the first occurrence of the specified value
     */
    @CacheInvalidate
    public void delete(int key) {
        if (head == null) return;

        if (head.data == key) {
            head = head.next;
            return;
        }

        JavaNode current = head;
        while (current.next != null && current.next.data != key) {
            current = current.next;
        }

        if (current.next != null) {
            current.next = current.next.next;
        }
    }

    /**
     * Search for a value in the list
     */
    @Memoize(maxSize = 64)
    public boolean search(int key) {
        JavaNode current = head;
        while (current != null) {
            if (current.data == key) return true;
            current = current.next;
        }
        return false;
    }

    /**
     * Get the length of the list
     */
    @Memoize
    public int length() {
        int count = 0;
        JavaNode current = head;
        while (current != null) {
            count++;
            current = current.next;
        }
        return count;
    }

    /**
     * Print the list to console
     */
    public void printList() {
        JavaNode current = head;
        while (current != null) {
            System.out.print(current.data + " -> ");
            current = current.next;
        }
        System.out.println("null");
    }

    /**
     * Traverse the list and return array of values
     */
    public int[] traverse() {
        int len = length();
        int[] values = new int[len];
        JavaNode current = head;
        int index = 0;
        while (current != null) {
            values[index++] = current.data;
            current = current.next;
        }
        return values;
    }

    /**
     * Traverse the list using iterator and return array of values
     */
    public int[] traverseWithIterator() {
        int len = length();
        int[] result = new int[len];
        int i = 0;

        Iterator<JavaNode> iterator = new JavaLinkedListIterator(head);
        while (iterator.hasNext()) {
            JavaNode node = iterator.next();
            result[i++] = node.data;
        }

        return result;
    }

    @Override
    public Iterator<JavaNode> iterator() {
        return new JavaLinkedListIterator(head);
    }
}
