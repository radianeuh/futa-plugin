package com.github.futa.dto;

public class ParsedMessage {
    public final String username;
    public final String content;

    public ParsedMessage(String username, String content) {
        this.username = username;
        this.content = content;
    }
}
