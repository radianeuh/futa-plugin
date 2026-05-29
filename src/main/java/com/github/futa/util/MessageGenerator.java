package com.github.futa.util;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class MessageGenerator {

    private static final Random random = new Random();

    // 求救消息模板（前缀部分，后接坐标）
    private static final List<String> HELP_MESSAGES = Arrays.asList(
            "救命, 我在：",
            "快救救我！坐标：",
            "大佬救我，位置：",
            "我卡住了！我在：",
            "被大手子追！我在：",
            "掉进洞了，求救：",
            "迷路了...我在：",
            "急需支援！位置：",
            "被围攻！坐标：",
            "要死了，我在："
    );


    private static final List<String> FOLLOW_MESSAGES = Arrays.asList(
            "我跟着你了，大佬。",
            "我已经在你后面了，不乱跑。",
            "从现在开始我跟着你混了。",
            "我默不作声地跟在你后面。",
            "你走你的，我跟我的，不掉队。",
            "我已经锁定你的背影了，跟定了。",
            "我不说话，就跟着你探索世界。",
            "你前进，我跟随，全程贴身保护（不是）。",
            "我已经把你设为领航员，开始跟随。",
            "脚步一致，方向一致，我跟上了。"
    );


    private static final List<String> TAUNTS = Arrays.asList(
            "我记下你ID了，回去就让我哥找你！",
            "你打我一下，我复活后一定告诉服主！",
            "别得意，我队友马上就到……其实我在喊人了！",
            "我空手是因为刚出生，等我找到剑你死定了！",
            "你打吧，我反正没装备，爆不出东西的！",
            "我虽然打不过你，但我打字比你快！",
            "你杀我一次，我就在公告里骂你一天！",
            "我不怕你，我有10个号，今天刚开这个！",
            "等我发育起来，我要在你家旁边建刷怪塔！",
            "你杀我？我这就去你家门口挖坟立碑！",
            "我背包里全是举报凭证，你等着被封号吧！",
            "我虽然空手，但我有梦想，梦想就是干掉你！",
            "你打我，我死后会在你家屋顶种仙人掌！",
            "我不是弱，我只是在测试你的道德底线！",
            "你杀我一次，我就在世界频道喊你妈妈！",
            "我死了也会化作幽灵跟着你，刷屏骂你！",
            "你别走，我刚学会打字，还没打完呢！",
            "我空手是因为我不想伤害别人，但你不配被原谅！",
            "等我找到钻石，第一个做命名剑来砍你！",
            "你打我吧，我的眼泪会变成末影珍珠，迟早找到你！",
            "你打我？我这血条是充话费送的，刷不掉！",
            "别以为你有盔甲就了不起，我有‘无敌帧’心理暗示！",
            "你杀我一次，我就在服务器公告写‘某人专欺负萌新’！",
            "我空手是因为我怕一拳把你账号打注销了！",
            "你追我干嘛？我的背包里只有空气和梦想！",
            "你砍我十刀掉一滴血，我砍你一眼你就得看心理医生！",
            "别得意，我刚截图了，已经发到‘全球欺负萌新通缉榜’！",
            "你打的是我？不，你打的是未来全服第一！",
            "我这不是逃跑，是在给你制造追击的快感，谢谢配合！",
            "你赢了战斗，但输了格局——我可是整活区顶流！",
            "杀我？你号都充不到月卡吧，也配叫大佬？",
            "我空手是因为刚删号重练，你当我是萌新就欺负？我以前砍人砍到手抽筋！",
            "别以为你一身钻石我就怕你了，我背包里可是有举报按钮的！",
            "你打我一下，我截图发贴吧，标题就叫《某服人机欺负萌新实录》",
            "笑死，你这身装备不会是工作室白给的吧？穿这么骚也没人组你？",
            "我死了会变成幽灵在世界频道刷：‘XXX专杀萌新’，你信不信？",
            "你砍我十刀，我掉一级；我骂你一句，你破防一整天！",
            "别得意，我队友是服主表弟，已经截图发群里了，等死吧",
            "你以为你在PVP？不，你正在参与我的‘整活素材收集计划’",
            "你杀我一次，我就在你家门口气象站立永久牌子：‘此地埋葬XXX尊严’"
    );


    /**
     * 生成并打印一条随机的求救消息，包含整数化后的坐标
     *
     * @param x X坐标（double）
     * @param y Y坐标（double）
     * @param z Z坐标（double）
     */
    public static String getHelpMessage(double x, double y, double z) {
        // 转为整数（Minecraft 通常使用整数坐标）
        int intX = (int) x;
        int intY = (int) y;
        int intZ = (int) z;

        // 随机选择一条模板
        String baseMessage = HELP_MESSAGES.get(random.nextInt(HELP_MESSAGES.size()));

        // 拼接最终消息
        String message = baseMessage + intX + " " + intY + " " + intZ;
        return (message);
    }


    /**
     * 随机返回一条“跟着你走”的消息
     *
     * @return 随机选中的消息字符串
     */
    public static String getRandomFollowMessage() {
        int index = random.nextInt(FOLLOW_MESSAGES.size());
        return FOLLOW_MESSAGES.get(index);
    }

    /**
     * 随机返回一条萌新嘴炮狠话
     *
     * @return 一条“狠话”
     */
    public static String getRandomTaunt() {
        return TAUNTS.get(random.nextInt(TAUNTS.size()));
    }

}
