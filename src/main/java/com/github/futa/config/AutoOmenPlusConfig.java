package com.github.futa.config;

public class AutoOmenPlusConfig {
    public boolean enabled = false;
    /**
     * 提前量（tick）。在<袭击之兆>结束的多少tick前喝药
     * 当"袭击之兆"效果的剩余持续时间 >= 此值时，视为仍有足够的倒计时，无需喝药；
     * 当剩余时间 < 此值时触发喝药。
     */
    public int before = 30;
    /**
     * 保留一瓶。若启用，则查找药水时要求药水堆叠数量 > 1，确保至少留一瓶在背包中。
     */
    public boolean one = true;
    /**
     * 喝药冷却时间（秒，0~10）。
     * 从开始喝药动作算起，最多持续尝试喝药这么长时间。
     * 超时后即使仍未获得效果，也会强制停止喝药并恢复杀戮光环，防止卡死。
     */
    public int drinkTimeout = 5;
    /**
     * 喝药计时日志开关。
     * 关闭后不再输出"喝药计时：x秒"的控制台信息，减少刷屏。
     */
    public boolean debug = true;
}
