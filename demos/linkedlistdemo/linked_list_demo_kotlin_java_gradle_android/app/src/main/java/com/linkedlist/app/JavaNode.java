package com.linkedlist.app;

/**
 * Node class representing a single element in the LinkedList
 */
public class JavaNode {
    int data;
    JavaNode next;

    public JavaNode(int data) {
        this.data = data;
        this.next = null;
    }
    
    public int getData() {
        return data;
    }

    public JavaNode getNext() {
        return next;
    }

    public void setNext(JavaNode next) {
        this.next = next;
    }
}
