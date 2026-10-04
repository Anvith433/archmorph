package com.library.repository;

import com.library.entity.Member;
import java.util.ArrayList;
import java.util.List;

public class MemberRepository {
    private final List<Member> items = new ArrayList<>();

    public void save(Member item) {
        items.add(item);
    }

    public List<Member> findAll() {
        return items;
    }
}
