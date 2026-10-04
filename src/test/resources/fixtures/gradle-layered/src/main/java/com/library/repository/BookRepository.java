package com.library.repository;

import com.library.entity.Book;
import java.util.ArrayList;
import java.util.List;

public class BookRepository {
    private final List<Book> items = new ArrayList<>();

    public void save(Book item) {
        items.add(item);
    }

    public List<Book> findAll() {
        return items;
    }
}
