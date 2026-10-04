package com.library.service;

import com.library.dto.MemberDto;
import com.library.entity.Member;
import com.library.repository.MemberRepository;

public class MemberService {
    private final MemberRepository repository;

    public MemberService(MemberRepository repository) {
        this.repository = repository;
    }

    public MemberDto create(MemberDto dto) {
        Member entity = new Member();
        entity.setId(dto.id);
        entity.setName(dto.name);
        repository.save(entity);
        return dto;
    }

    public int count() {
        return repository.findAll().size();
    }
}
