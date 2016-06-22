package flavor.pie.util.arguments;

import com.google.common.collect.ImmutableList;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.ArgumentParseException;
import org.spongepowered.api.command.args.CommandArgs;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.args.CommandElement;
import org.spongepowered.api.text.Text;

import javax.annotation.Nullable;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;

public class MoreArguments {
    private MoreArguments() {} //nope
    public static CommandElement url(Text key) {
        return new URIElement(key, false);
    }
    public static CommandElement uri(Text key) {
        return new URIElement(key, true);
    }
    private static class URIElement extends CommandElement {
        private final boolean returnURI;
        protected URIElement(@Nullable Text key, boolean returnURI) {
            super(key);
            this.returnURI = returnURI;
        }

        @Nullable
        @Override
        protected Object parseValue(CommandSource source, CommandArgs args) throws ArgumentParseException {
            String str = args.next();
            URL url;
            try {
                url = new URL(str);
            } catch (MalformedURLException ex) {
                throw new ArgumentParseException(Text.of("Invalid URL!"), ex, str, 0);
            }
            URI uri;
            try {
                uri = url.toURI();
            } catch (URISyntaxException ex) {
                throw new ArgumentParseException(Text.of("Invalid URL!"), ex, str, 0);
            }
            if (returnURI) {
                return uri;
            } else {
                return url;
            }
        }

        @Override
        public List<String> complete(CommandSource src, CommandArgs args, CommandContext context) {
            return ImmutableList.of();
        }
    }
}
