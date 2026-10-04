package com.library;

import com.library.controller.BookController;
import com.library.repository.BookRepository;
import com.library.service.BookService;
import com.library.controller.MemberController;
import com.library.repository.MemberRepository;
import com.library.service.MemberService;

public class LibraryApplication {

    public static void main(String[] args) {
        BookController book = new BookController(new BookService(new BookRepository()));
        MemberController member = new MemberController(new MemberService(new MemberRepository()));
        System.out.println("started");
    }
}
