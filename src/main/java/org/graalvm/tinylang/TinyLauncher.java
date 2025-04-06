package org.graalvm.tinylang;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.graalvm.launcher.AbstractLanguageLauncher;
import org.graalvm.options.OptionCategory;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Context.Builder;
import org.graalvm.polyglot.Source;

public final class TinyLauncher extends AbstractLanguageLauncher {

    private List<String> files = new ArrayList<>();
    private String[] programArgs;


    @Override
    protected String getLanguageId() {
        return "tiny";
    }

    
    @Override
    protected void launch(Builder b) {
        b.allowAllAccess(true);
        b.arguments(getLanguageId(), programArgs);
        b.allowExperimentalOptions(true);
        b.useSystemExit(true);
        Context c = b.build();
        
        List<Source> sources = new ArrayList<>();
        for (String file : files) {
            try {
                sources.add(Source.newBuilder(getLanguageId(), new File(file)).build());
            } catch (IOException e) {
                throw abort(e);
            }
        }
        
        for (Source source : sources) {
            System.out.println(c.eval(source));
        }
    }
    
    protected List<String> preprocessArguments(List<String> arguments, Map<String, String> polyglotOptions) {
        List<String> unrecognizedOptions = new ArrayList<>();
        ListIterator<String> iterator = arguments.listIterator();
        while (iterator.hasNext()) {
            String arg = iterator.next();
            if (arg.length() >= 2 && arg.startsWith("-")) {
                if (arg.equals("--")) {
                    break;
                }

                parseOption(iterator, arg);

                // support launcher specific options
                
                unrecognizedOptions.add(arg);
            } else {
                files.add(arg);
            }
        }
        List<String> programArgsList = arguments.subList(iterator.nextIndex(), arguments.size());
        programArgs = programArgsList.toArray(String[]::new);
        return unrecognizedOptions;
    }
    
    record Option(String flag, String value) {
    }
    
    private Option parseOption(ListIterator<String> iterator, String arg) {
        String flag = "";
        if (arg.startsWith("--")) {
            flag = arg.substring(2);
        } 
        String value;
        int equalsIndex = flag.indexOf('=');
        boolean hasEquals = equalsIndex > 0;
        if (hasEquals) {
            value = flag.substring(equalsIndex + 1);
            flag = flag.substring(0, equalsIndex);
        } else {
            value = null;
        }
        return new Option(flag, value);
    }

    @Override
    protected void printHelp(OptionCategory maxCategory) {
    }

    public static void main(String[] args) {
        new TinyLauncher().launch(args);
    }
}
