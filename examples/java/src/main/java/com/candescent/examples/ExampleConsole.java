package com.candescent.examples;

/**
 * Console output for runnable examples. Uses {@code System.out} intentionally (not SLF4J).
 */
@SuppressWarnings("java:S106")
public final class ExampleConsole {
    private ExampleConsole() {}

    public static void println() {
        System.out.println();
    }

    public static void println(String line) {
        System.out.println(line);
    }

    public static void print(String text) {
        System.out.print(text);
    }

    public static void errPrintln(String line) {
        System.err.println(line);
    }

    /** Print a completion line after OkHttp background work has finished (avoids exec-maven-plugin warnings). */
    public static void done() {
        done("\nDone.");
    }

    public static void done(String message) {
        ExampleHelpers.quiesceOkHttp();
        println(message);
    }
}
