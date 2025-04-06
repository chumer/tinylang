package org.graalvm.tinylang;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.graalvm.aotjs.api.JS;
import org.graalvm.aotjs.api.JSBoolean;
import org.graalvm.aotjs.api.JSObject;
import org.graalvm.aotjs.api.JSString;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

public final class TinyLauncher2 {

    public static final JSObject COMPILE_BUTTON = getElementById("compile");
    public static final JSObject INPUT = getElementById("source");
    public static final JSObject OUTPUT = getElementById("output");

    public static void main(String[] args) {
        try {
            // TODO GR-62854 Here to ensure handleEvent and run is generated. Remove once
            // objects
            // passed to @JS methods automatically have their SAM registered.
            sink(EventHandler.class.getDeclaredMethod("handleEvent", JSObject.class));
            sink(Runnable.class.getDeclaredMethod("run"));
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }

        addEventListener(COMPILE_BUTTON, "click", _ -> runCallback());
        addEventListener(INPUT, "keydown", e -> {
            if (e.get("ctrlKey") instanceof JSBoolean b && b.asBoolean() && e.get("key") instanceof JSString jsString
                    && "Enter".equals(jsString.asString())) {
                ((JSObject) e.get("preventDefault")).call(e);
                runCallback();
            }
        });
        setDisabled(false);
    }

    private static void runCallback() {
        setDisabled(true);
        runAsync(() -> {
            try {
                run();
            } finally {
                setDisabled(false);
            }
        });
    }
    

    public static void run() {
        resetOutput();
        String source = getSource();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            long time = System.nanoTime();
            try (Context c = Context.newBuilder("tiny").option("engine.WarnInterpreterOnly", "false").out(out).err(out).logHandler(out).build()) {
                Value result = c.eval("tiny", source);
                appendOutput(String.format("Eval finished in %.2fms", ((System.nanoTime() - time) / (double)1000000)));
                appendOutput("Result: " + result.toString());
            }
            out.flush();
            appendOutput(new String(out.toByteArray()));
        } catch (Throwable t) {
            ByteArrayOutputStream errorOut = new ByteArrayOutputStream();
            t.printStackTrace(new PrintStream(errorOut));
            appendOutput(new String(errorOut.toByteArray()));
        }
    }


    @JS("")
    private static native void sink(Object o);

    /**
     * Runs the given {@link Runnable} in {@code setTimeout} without delay.
     * <p>
     * Use this to let the browser repaint before running other code (otherwise
     * repainting is blocked until the Java code returns).
     */
    @JS.Coerce
    @JS("setTimeout(r, 0);")
    private static native void runAsync(Runnable r);

    private static void runWithUncaughtHandler(Runnable r) {
        try {
            r.run();
        } catch (Throwable e) {
            System.err.println("Uncaught exception in event listener");
            e.printStackTrace();
        }
    }

    @JS.Coerce
    @JS("o.addEventListener(event, (e) => handler(e));")
    static native void addEventListenerImpl(JSObject o, String event, EventHandler handler);

    static void addEventListener(JSObject o, String event, EventHandler handler) {
        addEventListenerImpl(o, event, e -> runWithUncaughtHandler(() -> handler.handleEvent(e)));
    }

    @JS.Coerce
    @JS("return document.getElementById(id);")
    public static native JSObject getElementById(String id);

    @JS.Coerce
    @JS("return document.createElement(tag);")
    public static native JSObject createElement(String tag);

    @JS.Coerce
    @JS("return document.createTextNode(text);")
    public static native JSObject createTextNode(String text);

    @JS.Coerce
    @JS("elem.setAttribute(attribute, value);")
    public static native void setAttribute(JSObject elem, String attribute, Object value);

    @JS.Coerce
    @JS("parent.appendChild(child);")
    public static native void appendChild(JSObject parent, JSObject child);

    public static String getSource() {
        return ((JSString) INPUT.get("innerText")).asString();
    }

    public static void resetOutput() {
        setDisabled(false);
        OUTPUT.set("innerHTML", "");
        OUTPUT.set("innerHTML", "");
    }

    public static void appendOutput(String val) {
        OUTPUT.set("innerHTML", ((JSString) OUTPUT.get("innerHTML")).asString() + val + '\n');
    }

    private static void setDisabled(boolean state) {
        setAttribute(INPUT, "contenteditable", JSBoolean.of(!state));
        COMPILE_BUTTON.set("disabled", JSBoolean.of(state));
    }

    @FunctionalInterface
    interface EventHandler {
        void handleEvent(JSObject event);
    }
}
