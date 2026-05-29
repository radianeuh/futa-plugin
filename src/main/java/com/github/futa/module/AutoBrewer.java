//package com.github.futa.module;
//
//import com.alibaba.cola.statemachine.StateMachine;
//import com.alibaba.cola.statemachine.builder.StateMachineBuilder;
//import com.alibaba.cola.statemachine.builder.StateMachineBuilderFactory;
//
//public class AutoBrewer {
//
//    enum States {
//        PREPARE, GO_TO_BREWING, ADD_FUEL, ADD_INGREDIENTS, BREWING, COLLECT, DONE
//    }
//
//    enum Events {
//        READY, ARRIVED, FUEL_ADDED, INGREDIENTS_ADDED, BREW_START, BREW_FINISH, COLLECTED, REPEAT
//    }
//
//    private final StateMachine<States, Events, AutoBrewer> stateMachine;
//
//    public AutoBrewer() {
//        StateMachineBuilder<States, Events, AutoBrewer> builder = StateMachineBuilderFactory.create();
//
//        // === 状态流转定义 ===
//        builder.externalTransition()
//                .from(States.PREPARE).to(States.GO_TO_BREWING).on(Events.READY)
//                .perform((from, to, event, ctx) -> ctx.goToBrewingStand());
//
//        builder.externalTransition()
//                .from(States.GO_TO_BREWING).to(States.ADD_FUEL).on(Events.ARRIVED)
//                .perform((from, to, event, ctx) -> ctx.addFuel());
//
//        builder.externalTransition()
//                .from(States.ADD_FUEL).to(States.ADD_INGREDIENTS).on(Events.FUEL_ADDED)
//                .perform((from, to, event, ctx) -> ctx.addIngredients());
//
//        builder.externalTransition()
//                .from(States.ADD_INGREDIENTS).to(States.BREWING).on(Events.INGREDIENTS_ADDED)
//                .perform((from, to, event, ctx) -> ctx.startBrewing());
//
//        builder.externalTransition()
//                .from(States.BREWING).to(States.COLLECT).on(Events.BREW_FINISH)
//                .perform((from, to, event, ctx) -> ctx.collectPotions());
//
//        builder.externalTransition()
//                .from(States.COLLECT).to(States.PREPARE).on(Events.REPEAT)
//                .perform((from, to, event, ctx) -> ctx.prepareNext());
//
//        builder.externalTransition()
//                .from(States.COLLECT).to(States.DONE).on(Events.COLLECTED);
//
//        stateMachine = builder.build("AutoBrewerStateMachine");
//    }
//
//    public void start(AutoBrewer ctx) {
//        stateMachine.fireEvent(States.PREPARE, Events.READY, ctx);
//    }
//
//    public void goToBrewingStand() {
//        System.out.println("移动到酿造台...");
//        // TODO: baritone #goto brewing_stand
//    }
//
//    public void addFuel() {
//        System.out.println("放入烈焰粉作为燃料");
//        // TODO: 物品交互
//    }
//
//    public void addIngredients() {
//        System.out.println("放入材料 (地狱疣 + 其他)");
//        // TODO: 按照配方逐个添加
//    }
//
//    public void startBrewing() {
//        System.out.println("等待酿造完成...");
//        // TODO: 等待进度条 / Tick 监听
//    }
//
//    public void collectPotions() {
//        System.out.println("收集完成的药水");
//        // TODO: 取走药水
//    }
//
//    public void prepareNext() {
//        System.out.println("检查材料，准备下一轮");
//        // TODO: 如果有材料 → fireEvent(REPEAT)，否则 → fireEvent(COLLECTED)
//    }
//}
