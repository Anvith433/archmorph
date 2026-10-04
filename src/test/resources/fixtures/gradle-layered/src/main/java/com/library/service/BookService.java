package com.library.service;

import com.library.dto.BookDto;
import com.library.entity.Book;
import com.library.repository.BookRepository;

public class BookService {
    private final BookRepository repository;

    public BookService(BookRepository repository) {
        this.repository = repository;
    }

    public BookDto create(BookDto dto) {
        Book entity = new Book();
        entity.setId(dto.id);
        entity.setName(dto.name);
        repository.save(entity);
        return dto;
    }

    public int count() {
        return repository.findAll().size();
    }
}
