package com.linkedlist.app;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LinkedListTest {
    private LinkedList list;

    @BeforeEach
    void setUp() {
        list = new LinkedList();
    }

    @Test
    void testInsert() {
        list.insert(10);
        assertEquals(1, list.length());
        list.insert(20);
        assertEquals(2, list.length());
    }

    @Test
    void testInsertAtHead() {
        list.insertAtHead(10);
        assertEquals(1, list.length());
        list.insertAtHead(5);
        assertEquals(2, list.length());
        assertEquals(5, list.traverse()[0]);
    }

    @Test
    void testDelete() {
        list.insert(10);
        list.insert(20);
        list.insert(30);
        list.delete(20);
        assertEquals(2, list.length());
        assertFalse(list.search(20));
        assertTrue(list.search(10));
        assertTrue(list.search(30));
    }

    @Test
    void testSearch() {
        list.insert(10);
        list.insert(20);
        assertTrue(list.search(10));
        assertTrue(list.search(20));
        assertFalse(list.search(30));
    }

    @Test
    void testLength() {
        assertEquals(0, list.length());
        list.insert(10);
        assertEquals(1, list.length());
        list.insert(20);
        assertEquals(2, list.length());
    }
}
