package flavor.pie.util.arguments;

import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.hocon.HoconConfigurationLoader;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import org.spongepowered.api.command.CommandMessageFormatting;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.*;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.resourcepack.ResourcePack;
import org.spongepowered.api.resourcepack.ResourcePacks;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.serializer.TextParseException;
import org.spongepowered.api.text.serializer.TextSerializers;
import org.spongepowered.api.util.GuavaCollectors;
import org.spongepowered.api.util.StartsWithPredicate;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.*;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

//    public static CommandElement itemStack(Text key, ItemStack mergeWith) {
//        return new ItemStackElement(key, mergeWith);
//    }

    public static CommandElement choices(Text key, Function<CommandSource, Map<String, Object>> function) {
        return new SuppliedChoicesCommandElement(key, function, (src) -> function.apply(src).keySet().size() < 5);
    }

    public static CommandElement bigDecimal(Text key) {
        return new BigDecimalElement(key);
    }

    public static CommandElement choices(Text key, Function<CommandSource, Map<String, Object>> function, boolean showChoicesInUsage) {
        return new SuppliedChoicesCommandElement(key, function, (src) -> showChoicesInUsage);
    }

    public static CommandElement bigInteger(Text key) {
        return new BigIntegerElement(key);
    }

    private static class IpElement extends CommandElement {

        boolean self;

        protected IpElement(@Nullable Text key, boolean self) {
            super(key);
            this.self = self;
        }

        @Nullable
        @Override
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

    private static class BigDecimalElement extends CommandElement {

        protected BigDecimalElement(@Nullable Text key) {
            super(key);
        }

        @Nullable
        @Override
        protected Object parseValue(CommandSource source, CommandArgs args) throws ArgumentParseException {
            String next = args.next();
            try {
                return new BigDecimal(next);
            } catch (NumberFormatException ex) {
                throw args.createError(Text.of("Expected a number, but input "+next+" was not"));
            }
        }

        @Override
        public List<String> complete(CommandSource src, CommandArgs args, CommandContext context) {
            return ImmutableList.of();
        }
    }

    private static class BigIntegerElement extends CommandElement {

        protected BigIntegerElement(@Nullable Text key) {
            super(key);
        }

        @Nullable
        @Override
        protected Object parseValue(CommandSource source, CommandArgs args) throws ArgumentParseException {
            String integerString = args.next();
            try {
                return new BigInteger(integerString);
            } catch (NumberFormatException ex) {
                throw args.createError(Text.of("Expected an integer, but input "+integerString+" was not"));
            }
        }

        @Override
        public List<String> complete(CommandSource src, CommandArgs args, CommandContext context) {
            return ImmutableList.of();
        }
    }

    private static class InventorySlotElement extends CommandElement {

        protected InventorySlotElement(@Nullable Text key) {
            super(key);
        }

        @Nullable
        @Override
        protected Object parseValue(CommandSource source, CommandArgs args) throws ArgumentParseException {
            String arg = args.next();
            String[] split = arg.split("\\.");
            ArgumentParseException ex = args.createError(Text.of("Invalid slot!"));
            if (!split[0].equals("slot")) throw ex;
            switch (split[1]) {
                case "armor":
                    switch (split[2]) {
                        case "chest":

                    }
            }
            return null;
        }

        @Override
        public List<String> complete(CommandSource src, CommandArgs args, CommandContext context) {
            return null;
        }

        @Override
        public Text getUsage(CommandSource src) {
            return null;
        }
    }

    public static CommandElement uuid(Text key) {
        return new UUIDElement(key);
    }

    private static class UUIDElement extends KeyElement {

        protected UUIDElement(Text key) {
            super(key);
        }

        @Nullable
        @Override
        protected Object parseValue(CommandSource source, CommandArgs args) throws ArgumentParseException {
            try {
                return UUID.fromString(args.next());
            } catch (IllegalArgumentException ex) {
                throw args.createError(Text.of("Invalid UUID!"));
            }
        }

    }

    public static CommandElement text(Text key, boolean complex, boolean allRemaining) {
        return new TextCommandElement(key, complex, allRemaining);
    }

    private static class RemainingJoinedStringsCommandElement extends KeyElement {

        private final boolean raw;

        RemainingJoinedStringsCommandElement(Text key, boolean raw) {
            super(key);
            this.raw = raw;
        }

        @Override
        protected Object parseValue(CommandSource source, CommandArgs args) throws ArgumentParseException {
            if (this.raw) {
                args.next();
                String ret = args.getRaw().substring(args.getRawPosition());
                while (args.hasNext()) { // Consume remaining args
                    args.next();
                }
                return ret;
            } else {
                final StringBuilder ret = new StringBuilder(args.next());
                while (args.hasNext()) {
                    ret.append(' ').append(args.next());
                }
                return ret.toString();
            }
        }

        @Override
        public Text getUsage(CommandSource src) {
            return Text.of(CommandMessageFormatting.LT_TEXT, getKey(), CommandMessageFormatting.ELLIPSIS_TEXT, CommandMessageFormatting.GT_TEXT);
        }
    }

    private static class TextCommandElement extends KeyElement {

        private final boolean complex;
        private final boolean allRemaining;
        private final RemainingJoinedStringsCommandElement joinedElement;

        protected TextCommandElement(Text key, boolean complex, boolean allRemaining) {
            super(key);
            this.complex = complex;
            this.allRemaining = allRemaining;
            joinedElement = allRemaining ? new RemainingJoinedStringsCommandElement(key, false) : null;
        }

        @Nullable
        @Override
        protected Object parseValue(CommandSource source, CommandArgs args) throws ArgumentParseException {
            String arg = this.allRemaining ? (String) joinedElement.parseValue(source, args) : args.next();
            if (this.complex) {
                try {
                    return TextSerializers.JSON.deserialize(arg);
                } catch (TextParseException ex) {
                    throw args.createError(Text.of("Invalid JSON text: ", ex.getMessage()));
                }
            } else {
                return TextSerializers.FORMATTING_CODE.deserialize(arg);
            }
        }
    }

    public static CommandElement dateTime(Text key) {
        return new DateTimeElement(key, false);
    }

    public static CommandElement dateTimeOrNow(Text key) {
        return new DateTimeElement(key, true);
    }

    private static class DateTimeElement extends CommandElement {

        private final boolean returnNow;

        protected DateTimeElement(Text key, boolean returnNow) {
            super(key);
            this.returnNow = returnNow;
        }

        @Nullable
        @Override
        protected Object parseValue(CommandSource source, CommandArgs args) throws ArgumentParseException {
            if (!args.hasNext() && this.returnNow) {
                return LocalDateTime.now();
            }
            Object state = args.getState();
            String date = args.next();
            try {
                return LocalDateTime.parse(date);
            } catch (DateTimeParseException ex) {
                try {
                    return LocalDateTime.of(LocalDate.now(), LocalTime.parse(date));
                } catch (DateTimeParseException ex2) {
                    try {
                        return LocalDateTime.of(LocalDate.parse(date), LocalTime.MIDNIGHT);
                    } catch (DateTimeParseException ex3) {
                        if (this.returnNow) {
                            args.setState(state);
                            return LocalDateTime.now();
                        }
                        throw args.createError(Text.of("Invalid date-time!"));
                    }
                }
            }
        }

        @Override
        public List<String> complete(CommandSource src, CommandArgs args, CommandContext context) {
            String date = LocalDateTime.now().withNano(0).toString();
            if (date.startsWith(args.nextIfPresent().orElse(""))) {
                return ImmutableList.of(date);
            } else {
                return ImmutableList.of();
            }
        }

        @Override
        public Text getUsage(CommandSource src) {
            if (!this.returnNow) {
                return super.getUsage(src);
            } else {
                return Text.of("[", this.getKey(), "]");
            }
        }
    }

    public static CommandElement duration(Text key) {
        return new DurationElement(key);
    }

    private static class DurationElement extends KeyElement {

        protected DurationElement(Text key) {
            super(key);
        }

        @Nullable
        @Override
        protected Object parseValue(CommandSource source, CommandArgs args) throws ArgumentParseException {
            String s = args.next();
            if (!s.startsWith("P") && !s.startsWith("p")) {
                s = "p" + s;
            }
            try {
                return Duration.parse(s);
            } catch (DateTimeParseException ex) {
                throw args.createError(Text.of("Invalid duration!"));
            }
        }
    }
    private abstract static class KeyElement extends CommandElement {

        private KeyElement(Text key) {
            super(key);
        }

        @Override
        public List<String> complete(CommandSource src, CommandArgs args, CommandContext context) {
            return Collections.emptyList();
        }
    }
}
