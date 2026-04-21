package com.linkedlist.app;

import java.util.Iterator;

/**
 * LinkedList implementation with basic operations
 */
public class LinkedList implements Iterable<Node> {
    private Node head;

    /**
     * Insert a new element at the end of the list
     */
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

    /**
     * Insert a new element at the beginning of the list
     */
    public void insertAtHead(int data) {
        Node newNode = new Node(data);
        newNode.next = head;
        head = newNode;
    }

    /**
     * Delete the first occurrence of the specified value
     */
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

    /**
     * Search for a value in the list
     */
    public boolean search(int key) {
        Node current = head;
        while (current != null) {
            if (current.data == key) return true;
            current = current.next;
        }
        return false;
    }

    /**
     * Get the length of the list
     */
    public int length() {
        int count = 0;
        Node current = head;
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
        Node current = head;
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
        Node current = head;
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

        Iterator<Node> iterator = new LinkedListIterator(head);
        while (iterator.hasNext()) {
            Node node = iterator.next();
            result[i++] = node.data;
        }

        return result;
    }

    @Override
    public Iterator<Node> iterator() {
        return new LinkedListIterator(head);
    }
}
