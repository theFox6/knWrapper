package com.theFox6.knWrapper;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum WrapperArgument {
    HELP("h", "help", "shows all command line options"),
    UPDATE("u", "update", "initially check for updates before start"),
    INSTALL("i", "install", "install KleinerNerd if it is not present"),
    FULLLOG("f", "fullog", "enable full logging mode"),
    CHECKS("c","checks", "tell KleinerNerd to run checks and then quit without logging on");

    private static Map<String, WrapperArgument> shortOpts;
    private static Map<String, WrapperArgument> longOpts;
    public final String s;
    public final String l;
    public final String d;

    WrapperArgument(String shortOpt, String longOpt, String desc) {
        this.s = shortOpt;
        this.l = longOpt;
        this.d = desc;
    }

    public String toString() {
        return '-' + s + "\t--" + l + '\t' + d;
    }

    public static WrapperArgument getLongOpt(String option) {
        if (longOpts == null)
            longOpts = Arrays.stream(WrapperArgument.values()).collect(Collectors.toMap((o) -> o.l, Function.identity()));
        return longOpts.get(option);
    }

    public static WrapperArgument getShortOpt(String option) {
        if (shortOpts == null)
            shortOpts = Arrays.stream(WrapperArgument.values()).collect(Collectors.toMap((o) -> o.s, Function.identity()));
        return shortOpts.get(option);
    }

    public static void printHelp() {
        System.out.println("KleinerNerd Wrapper");
        System.out.println();
        System.out.println("command line arguments:");
        Arrays.stream(WrapperArgument.values())
                .map(WrapperArgument::toString)
                .forEach(System.out::println);
    }
}
