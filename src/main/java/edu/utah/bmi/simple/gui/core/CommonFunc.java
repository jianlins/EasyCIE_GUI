package edu.utah.bmi.simple.gui.core;

import org.apache.commons.cli.*;

import java.io.File;

/**
 * Created by Jianlin Shi on 8/4/17.
 */
public class CommonFunc {

    public static void addOption(Options options, String shortArg, String longArg, String description, String required, String takeValue) {
        Option opt = new Option(shortArg, longArg, takeValue.equals("1"), description);
        opt.setRequired(required.equals("1"));
        options.addOption(opt);
    }

    public static String getCmdValue(CommandLine cmd, String key, String defaultValue) {
        String value = cmd.getOptionValue(key);
        if (value == null)
            value = defaultValue;
        return value;
    }

    public static CommandLine parseArgs(Options options, String[] args, String commandName) {
        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        formatter.setWidth(130);
        formatter.setNewLine("\n\n");
        formatter.setOptionComparator(null);
        CommandLine cmd;
        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp(commandName, options);
            System.exit(1);
            return null;
        }
        if (cmd.hasOption("h")) {
            formatter.printHelp(commandName, options);
            System.exit(1);
        }
        return cmd;
    }

    public static boolean checkFileExist(File file, String intructionPrefix) {
        if (!file.exists()) {
            System.err.println(intructionPrefix + ": " + file.getAbsolutePath() + " does not exist.");
            return false;
        } else if (!file.isFile()) {
            System.err.println(intructionPrefix + ": " + file.getAbsolutePath() + " is not a file.");
            return false;
        }
        return false;
    }

    public static boolean checkDirExist(File file, String intructionPrefix) {
        if (!file.exists()) {
            System.err.println(intructionPrefix + ": " + file.getAbsolutePath() + " does not exist.");
            return false;
        } else if (file.isFile()) {
            System.err.println(intructionPrefix + ": " + file.getAbsolutePath() + " is not a directory.");
            return false;
        }
        return false;
    }
}
