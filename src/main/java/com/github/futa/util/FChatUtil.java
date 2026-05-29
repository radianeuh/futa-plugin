package com.github.futa.util;

import com.github.futa.dto.ParsedMessage;

public class FChatUtil {


    public static ParsedMessage parsePrivateMessage(String msg) {
        // 正则：📨 后跟空格 + 用户名 + 空格 + ➡ + 空格 + 消息
        String regex = "^📨\\s+([^➡]+?)\\s+➡\\s+(.+)$";
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(regex);
        java.util.regex.Matcher matcher = pattern.matcher(msg);

        if (matcher.matches()) {
            String username = matcher.group(1).trim();
            String content = matcher.group(2).trim();
            return new ParsedMessage(username, content);
        }
        return null; // 不是私信格式
    }

}
