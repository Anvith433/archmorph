package com.blog.core.comment;

public class Comment {
    private final String id;
    private final String body;
    private final String userId;

    public Comment(String id, String body, String userId) {
        this.id = id;
        this.body = body;
        this.userId = userId;
    }

    public String getId() { return id; }
    public String getBody() { return body; }
    public String getUserId() { return userId; }
}
