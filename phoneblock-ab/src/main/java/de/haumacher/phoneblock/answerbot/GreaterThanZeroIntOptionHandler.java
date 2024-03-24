package de.haumacher.phoneblock.answerbot;

import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.IntOptionHandler;
import org.kohsuke.args4j.spi.Setter;

public class GreaterThanZeroIntOptionHandler extends IntOptionHandler {
    public GreaterThanZeroIntOptionHandler(CmdLineParser parser, OptionDef option, Setter<? super Integer> setter) {
        super(parser, option, setter);
    }

    @Override
    protected Integer parse(String argument) throws NumberFormatException {
        int value =  super.parse(argument);
        if ( value < 1) {
            throw new NumberFormatException("value less than one");
        }
        return value;
    }


}
