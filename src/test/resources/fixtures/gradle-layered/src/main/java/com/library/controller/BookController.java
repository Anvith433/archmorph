package com.library.controller;

import com.library.dto.BookDto;
import com.library.service.BookService;

public class BookController {
    private final BookService service;

    public BookController(BookService service) {
        this.service = service;
    }

    public BookDto create(BookDto dto) {
        return service.create(dto);
    }
}
