package com.ryjs.intercept.util.kp;

import net.neoforged.bus.BusBuilderImpl;
import net.neoforged.bus.EventBus;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventListener;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;

import java.util.function.Consumer;


public class FakeEventBus extends EventBus {
    public FakeEventBus(BusBuilderImpl busBuilder) {
        super(busBuilder);
    }

    @Override
    public <T extends Event> void addListener(final Consumer<T> consumer) {
        return;
    }

    @Override
    public <T extends Event> void addListener(final EventPriority priority, final Consumer<T> consumer) {
        return;
    }

    @Override
    public <T extends Event> void addListener(final EventPriority priority, final boolean receiveCanceled, final Consumer<T> consumer) {
        return;
    }

    @Override
    public <T extends Event> void addListener(EventPriority priority, Class<T> eventType, Consumer<T> consumer) {
        return;
    }

    @Override
    public <T extends Event> void addListener(boolean receiveCanceled, Consumer<T> consumer) {
        return;
    }

    @Override
    public <T extends Event> void addListener(boolean receiveCanceled, Class<T> eventType, Consumer<T> consumer) {
        return;
    }

    @Override
    public <T extends Event> void addListener(Class<T> eventType, Consumer<T> consumer) {
        return;
    }

    @Override
    public <T extends Event> void addListener(final EventPriority priority, final boolean receiveCanceled, final Class<T> eventType, final Consumer<T> consumer) {
        return;
    }
    @Override
    public void unregister(Object object) {
        return;
    }

    @Override
    public <T extends Event> T post(T event) {


        return event;
    }

    @Override
    public <T extends Event> T post(EventPriority phase, T event) {
        return event;
    }

    @Override
    public void handleException(IEventBus bus, Event event, EventListener[] listeners, int index, Throwable throwable) {
        return;
    }

    @Override
    public void start() {
        return;
    }




}
