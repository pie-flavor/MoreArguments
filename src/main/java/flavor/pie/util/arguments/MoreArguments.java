package flavor.pie.util.arguments;

import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.hocon.HoconConfigurationLoader;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import org.apache.commons.lang3.NotImplementedException;
import org.spongepowered.api.command.CommandMessageFormatting;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.ArgumentParseException;
import org.spongepowered.api.command.args.CommandArgs;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.args.CommandElement;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.resourcepack.ResourcePack;
import org.spongepowered.api.resourcepack.ResourcePacks;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.util.GuavaCollectors;
import org.spongepowered.api.util.StartsWithPredicate;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;
import java.net.*;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.spongepowered.api.util.SpongeApiTranslationHelper.t;

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
    public static CommandElement resourcePack(Text key) {
        return new ResourcePackElement(key);
    }
    private static class ResourcePackElement extends URIElement {
        protected ResourcePackElement(@Nullable Text key) {
            super(key, true);
        }
        @Nullable
        @Override
        protected Object parseValue(CommandSource source, CommandArgs args) throws ArgumentParseException {
            URI uri = (URI) super.parseValue(source, args);
            ResourcePack pack;
            try {
                pack = ResourcePacks.fromUri(uri);
            } catch (FileNotFoundException ex) {
                throw new ArgumentParseException(Text.of("No resource pack located at this URL!"), ex, uri.toString(), 0);
            }
            return pack;
        }
    }
    public static CommandElement ip(Text key) {
        return new IpElement(key, false);
    }
    public static CommandElement ipOrSource(Text key) {
        return new IpElement(key, true);
    }
    public static CommandElement hoconNode(Text key) {
        return new NodeElement(key);
    }
    public static CommandElement itemStack(Text key, ItemStack mergeWith) {
        throw new NotImplementedException("Use hoconNode!");
        //return new ItemStackElement(key, mergeWith);
    }
    public static CommandElement choices(Text key, Function<CommandSource, Map<String, Object>> function) {
        return new SuppliedChoicesCommandElement(key, function, (src) -> function.apply(src).keySet().size() < 5);
    }
    public static CommandElement choices(Text key, Function<CommandSource, Map<String, Object>> function, boolean showChoicesInUsage) {
        return new SuppliedChoicesCommandElement(key, function, (src) -> showChoicesInUsage);
    }
    private static class IpElement extends CommandElement {
        boolean self;
        protected IpElement(@Nullable Text key, boolean self) {
            super(key);
            this.self = self;
        }
        @Nullable @Override
        protected Object parseValue(CommandSource source, CommandArgs args) throws ArgumentParseException {
            if (!args.hasNext() && self)
                if (source instanceof Player)
                    return ((Player) source).getConnection().getAddress().getAddress();
                else throw args.createError(Text.of("No IP address was specified, and source was not a player!"));
            Object state = args.getState();
            String s = args.next();
            try {
                return InetAddress.getByName(s);
            } catch (UnknownHostException e) {
                if (self) {
                    if (source instanceof Player) {
                        args.setState(state);
                        return ((Player) source).getConnection().getAddress().getAddress();
                    } else throw args.createError(Text.of("Invalid IP address, and source was not a player!"));
                }
                throw args.createError(Text.of("Invalid IP address!"));
            }
        }
        @Override
        public List<String> complete(CommandSource src, CommandArgs args, CommandContext context) {
            return ImmutableList.of();
        }
        @Override
        public Text getUsage(CommandSource src) {
            return src instanceof Player && self ? Text.of("[", super.getUsage(src), "]") : super.getUsage(src);
        }
    }
    private static class NodeElement extends CommandElement {
        protected NodeElement(@Nullable Text key) {
            super(key);
        }
        @Nullable
        @Override
        protected Object parseValue(CommandSource source, CommandArgs args) throws ArgumentParseException {
            StringBuilder builder = new StringBuilder();
            while (args.hasNext()) {
                builder.append(" ").append(args.next());
            }
            String argument = builder.toString();
            try {
                return HoconConfigurationLoader.builder().setSource(() -> new BufferedReader(new StringReader(argument))).build().load();
            } catch (IOException e) {
                throw args.createError(Text.of("Node parsing failed: "+e.getMessage()));
            }
        }
        @Override
        public List<String> complete(CommandSource src, CommandArgs args, CommandContext context) {
            return ImmutableList.of();
        }
    }
    private static class ItemStackElement extends NodeElement {
        protected ItemStackElement(@Nullable Text key, ItemStack mergeWith) {
            super(key/*, ((Supplier<ConfigurationNode>) () -> {try {return HoconConfigurationLoader.builder().build().createEmptyNode().setValue(TypeToken.of(ItemStack.class), mergeWith);} catch (ObjectMappingException e) {throw new IllegalArgumentException();}}).get()*/);
        }
        @Nullable @Override
        public Object parseValue(CommandSource source, CommandArgs args) throws ArgumentParseException {
            ConfigurationNode node = (ConfigurationNode) super.parseValue(source, args);
            try {
                return node.getValue(TypeToken.of(ItemStack.class));
            } catch (ObjectMappingException e) {
                throw args.createError(Text.of("Could not parse ItemStack from node: "+e.getMessage()));
            }
        }
    }
    private static class SuppliedChoicesCommandElement extends CommandElement {
        private final Function<CommandSource, Map<String, Object>> choices;
        private final Predicate<CommandSource> choicesInUsage;

        SuppliedChoicesCommandElement(Text key, Function<CommandSource, Map<String, Object>> choices, Predicate<CommandSource> choicesInUsage) {
            super(key);
            this.choices = choices;
            this.choicesInUsage = choicesInUsage;
        }

        @Override
        public Object parseValue(CommandSource source, CommandArgs args) throws ArgumentParseException {
            Map<String, Object> currentChoices = choices.apply(source);
            Object value = currentChoices.get(args.next());
            if (value == null) {
                throw args.createError(t("Argument was not a valid choice. Valid choices: %s", currentChoices.keySet().toString()));
            }
            return value;
        }

        @Override
        public List<String> complete(CommandSource src, CommandArgs args, CommandContext context) {
            Map<String, Object> currentChoices = choices.apply(src);
            final String prefix = args.nextIfPresent().orElse("");
            return currentChoices.keySet().stream().filter(new StartsWithPredicate(prefix)).collect(GuavaCollectors.toImmutableList());
        }

        @Override
        public Text getUsage(CommandSource commander) {
            Map<String, Object> currentChoices = choices.apply(commander);
            if (this.choicesInUsage.test(commander)) {
                final Text.Builder build = Text.builder();
                build.append(CommandMessageFormatting.LT_TEXT);
                for (Iterator<String> it = currentChoices.keySet().iterator(); it.hasNext();) {
                    build.append(Text.of(it.next()));
                    if (it.hasNext()) {
                        build.append(CommandMessageFormatting.PIPE_TEXT);
                    }
                }
                build.append(CommandMessageFormatting.GT_TEXT);
                return build.build();
            } else {
                return super.getUsage(commander);
            }
        }
    }
}
