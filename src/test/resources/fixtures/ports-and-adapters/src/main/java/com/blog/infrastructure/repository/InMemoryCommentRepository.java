package com.blog.infrastructure.repository;

import com.blog.core.comment.Comment;
import com.blog.core.comment.CommentRepository;
import java.util.ArrayList;
import java.util.List;

public class InMemoryCommentRepository implements CommentRepository {
    private final List<Comment> comments = new ArrayList<>();

    @Override
    public void save(Comment comment) {
        comments.add(comment);
    }

    @Override
    public List<Comment> findByUser(String userId) {
        return comments.stream().filter(c -> c.getUserId().equals(userId)).toList();
    }
}
