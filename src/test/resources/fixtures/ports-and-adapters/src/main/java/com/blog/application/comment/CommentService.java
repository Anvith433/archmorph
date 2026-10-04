package com.blog.application.comment;

import com.blog.core.comment.Comment;
import com.blog.core.comment.CommentRepository;
import java.util.List;

public class CommentService {
    private final CommentRepository comments;

    public CommentService(CommentRepository comments) {
        this.comments = comments;
    }

    public Comment add(String id, String body, String userId) {
        Comment comment = new Comment(id, body, userId);
        comments.save(comment);
        return comment;
    }

    public List<Comment> byUser(String userId) {
        return comments.findByUser(userId);
    }
}
