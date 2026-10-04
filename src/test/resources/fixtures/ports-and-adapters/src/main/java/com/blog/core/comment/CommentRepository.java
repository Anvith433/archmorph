package com.blog.core.comment;

import java.util.List;

public interface CommentRepository {
    void save(Comment comment);

    List<Comment> findByUser(String userId);
}
