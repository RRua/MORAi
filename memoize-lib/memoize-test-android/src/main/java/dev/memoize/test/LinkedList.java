package dev.memoize.test;

import dev.memoize.annotations.CacheInvalidate;
import dev.memoize.annotations.Memoize;

/**
 * LinkedList implementation with @Memoize annotations on query methods
 * and @CacheInvalidate on mutating methods.
 */
public class LinkedList {
    private Node head;

    @CacheInvalidate({"search", "length", "describe"})
    public void insert(int data) {
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

    @CacheInvalidate({"search", "length", "describe"})
    public void insertAtHead(int data) {
        Node newNode = new Node(data);
        newNode.next = head;
        head = newNode;
    }

    @CacheInvalidate({"search", "length", "describe"})
    public void delete(int key) {
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

    @Memoize(maxSize = 64)
    public boolean search(int key) {
        Node current = head;
        while (current != null) {
            if (current.data == key) return true;
            current = current.next;
        }
        return false;
    }

    @Memoize(maxSize = 16)
    public int length() {
        int count = 0;
        Node current = head;
        while (current != null) {
            count++;
            current = current.next;
        }
        return count;
    }

    @Memoize(maxSize = 64)
    public String describe(int key) {
        Node current = head;
        while (current != null) {
            if (current.data == key) {
                return "Found: " + key;
            }
            current = current.next;
        }
        return null;
    }
}
