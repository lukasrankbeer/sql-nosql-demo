package com.demo.util;

public class Printer {

    private static final String RESET   = "\033[0m";
    private static final String BOLD    = "\033[1m";
    private static final String CYAN    = "\033[36m";
    private static final String GREEN   = "\033[32m";
    private static final String YELLOW  = "\033[33m";
    private static final String BLUE    = "\033[34m";
    private static final String MAGENTA = "\033[35m";
    private static final String RED     = "\033[31m";

    public static void header(String text) {
        System.out.println();
        System.out.println(BOLD + "═".repeat(65) + RESET);
        System.out.println(BOLD + CYAN + "  " + text + RESET);
        System.out.println(BOLD + "═".repeat(65) + RESET);
    }

    public static void section(String text) {
        System.out.println();
        System.out.println(BOLD + BLUE + "▶  " + text + RESET);
        System.out.println(BLUE + "─".repeat(65) + RESET);
    }

    public static void step(String text) {
        System.out.println();
        System.out.println(BOLD + GREEN + "  " + text + RESET);
    }

    public static void info(String text) {
        System.out.println(YELLOW + "    ℹ  " + text + RESET);
    }

    public static void result(String text) {
        System.out.println("       " + text);
    }

    public static void sql(String query) {
        System.out.println(CYAN + "    SQL ▸ " + query + RESET);
    }

    public static void nosql(String query) {
        System.out.println(MAGENTA + "    MDB ▸ " + query + RESET);
    }

    public static void error(String text) {
        System.out.println(RED + "    ✗  " + text + RESET);
    }

    public static void tableRow(String col1, String col2, String col3) {
        System.out.printf("  %-26s %-28s %-28s%n", col1, col2, col3);
    }

    public static void tableDivider() {
        System.out.println("  " + "─".repeat(82));
    }
}
