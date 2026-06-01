package com.github.futa.config;

public class AutoOmenPlusConfig {
    public boolean enabled = false;
    /**
     * 提前量（tick）。
     * 当"袭击之兆"效果的剩余持续时间 >= 此值时，视为仍有足够的倒计时，无需喝药；
     * 当剩余时间 < 此值时触发喝药。
     */
    public int before = 100;
    /**
     * 保留一瓶。若启用，则查找药水时要求药水堆叠数量 > 1，确保至少留一瓶在背包中。
     */
    public boolean one = true;
}
