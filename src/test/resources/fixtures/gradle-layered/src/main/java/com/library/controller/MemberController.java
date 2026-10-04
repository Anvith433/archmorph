package com.library.controller;

import com.library.dto.MemberDto;
import com.library.service.MemberService;

public class MemberController {
    private final MemberService service;

    public MemberController(MemberService service) {
        this.service = service;
    }

    public MemberDto create(MemberDto dto) {
        return service.create(dto);
    }
}
