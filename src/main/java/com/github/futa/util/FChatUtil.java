package com.github.futa.util;

import com.github.futa.dto.ParsedMessage;

import java.util.regex.Pattern;

public class FChatUtil {

    // 正则：📨 后跟空格 + 用户名 + 空格 + ➡ + 空格 + 消息
    static String regex = "^📨\\s+([^➡]+?)\\s+➡\\s+(.+)$";
    static Pattern pattern = Pattern.compile(regex);

    public static ParsedMessage parsePrivateMessage(String msg) {

        java.util.regex.Matcher matcher = pattern.matcher(msg);
        if (matcher.matches()) {
            String username = matcher.group(1).trim();
            String content = matcher.group(2).trim();
            return new ParsedMessage(username, content);
        }
        return null; // 不是私信格式
    }


}
