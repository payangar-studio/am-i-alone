package com.payangar.amialone.client.companion;

import com.payangar.amialone.companion.CompanionAttribute;
import com.payangar.amialone.companion.CompanionAttributeGroup;
import com.payangar.amialone.companion.CompanionAttributeValues.Computed;
import com.payangar.amialone.companion.CompanionAttributeValues.Value;
import com.payangar.amialone.companion.CompanionComputedValue;
import com.payangar.amialone.companion.CompanionViewMenu;
import com.payangar.amialone.vendor.gui.Draw;
import com.payangar.amialone.vendor.gui.Theme;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToIntFunction;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attribute;
import org.jspecify.annotations.Nullable;

/**
 * The drawer's Attributes panel: the companion's attributes and computed values in groups, a label
 * on the left and the value on the right, the full name with the current and base values in a
 * row's tooltip.
 *
 * <p>What it shows is data: the groups and the entries of the {@link CompanionAttributeGroup} and
 * {@link CompanionAttribute} registries, each in its order. The values are the ones the server sent
 * the menu, never the client companion's: the client builds a companion as a player, whose
 * attribute map is a player's. An entry the server sent no value for is not shown.
 *
 * <p>The rows scroll with the mouse wheel when they outgrow the panel.
 */
final class AttributesPanel {
    private static final String KEY = "container.am_i_alone.companion.attributes.";
    private static final DecimalFormat FORMAT = new DecimalFormat("#.##", DecimalFormatSymbols.getInstance(Locale.ROOT));
    /** Apothic Attributes' colours for a good and a bad change, readable on a dark panel. */
    private static final int GOOD = 0xFF55DD55;
    private static final int BAD = 0xFFFF6060;
    private static final String ELLIPSIS = "…";
    private static final int ROW_HEIGHT = 10;
    /** Between a group's last row and the next group's header. */
    private static final int GROUP_GAP = 4;
    /** The least space between a label and its value. */
    private static final int VALUE_GAP = 2;
    private static final int SCROLL_STEP = ROW_HEIGHT;
    private static final int SCROLLBAR_WIDTH = 2;

    private static final String EN_DASH = "\u2013";

    /** A row: its label, its value as it reads, the value's colour, and its tooltip. */
    private record Row(Component label, String value, int colour, List<Component> tooltip) {}

    /** A line drawn this frame: a header ({@code row} null) or a row with its values. */
    private record Line(int y, @Nullable Row row, Component text) {}

    private int scroll;

    /**
     * Draws the panel in the box at {@code x, y} of {@code width} by {@code height}, and the
     * hovered row's tooltip.
     */
    void extract(
            GuiGraphicsExtractor graphics,
            Font font,
            CompanionViewMenu menu,
            int x,
            int y,
            int width,
            int height,
            int mouseX,
            int mouseY,
            boolean hoverable) {
        List<Line> lines = lines(menu);
        int contentHeight = contentHeight(lines);
        scroll = Mth.clamp(scroll, 0, Math.max(0, contentHeight - height));
        graphics.enableScissor(x, y, x + width, y + height);
        @Nullable Line hovered = null;
        for (Line line : lines) {
            int top = y + line.y() - scroll;
            if (top + ROW_HEIGHT <= y || top >= y + height) {
                continue;
            }
            int textY = top + 1;
            if (line.row() == null) {
                graphics.text(font, fit(font, line.text(), width), x, textY, Theme.HEADING, false);
                continue;
            }
            Row row = line.row();
            int valueWidth = font.width(row.value());
            graphics.text(font, row.value(), x + width - valueWidth, textY, row.colour(), false);
            graphics.text(font, fit(font, line.text(), width - valueWidth - VALUE_GAP), x, textY, Theme.TEXT_DIM, false);
            if (hoverable
                    && mouseX >= x && mouseX < x + width
                    && mouseY >= Math.max(y, top) && mouseY < Math.min(y + height, top + ROW_HEIGHT)) {
                hovered = line;
            }
        }
        graphics.disableScissor();
        if (contentHeight > height) {
            int barX = x + width + 1;
            int thumb = Math.max(8, height * height / contentHeight);
            int thumbY = y + (height - thumb) * scroll / (contentHeight - height);
            Draw.fill(graphics, barX, y, SCROLLBAR_WIDTH, height, Theme.BACKGROUND);
            Draw.fill(graphics, barX, thumbY, SCROLLBAR_WIDTH, thumb, Theme.BORDER_HOVER);
        }
        if (hovered != null) {
            graphics.setComponentTooltipForNextFrame(font, hovered.row().tooltip(), mouseX, mouseY);
        }
    }

    /** Scrolls the rows by the wheel's {@code amount}; false when they fit and do not scroll. */
    boolean scroll(CompanionViewMenu menu, int height, double amount) {
        int max = contentHeight(lines(menu)) - height;
        if (max <= 0) {
            return false;
        }
        scroll = Mth.clamp(scroll - (int) Math.signum(amount) * SCROLL_STEP, 0, max);
        return true;
    }

    /**
     * The lines to draw, from the top of the content: each group's header, then its shown rows;
     * groups and rows in their order, then by id.
     */
    private static List<Line> lines(CompanionViewMenu menu) {
        RegistryAccess registries = menu.companion().registryAccess();
        Map<Holder<Attribute>, Value> values = menu.attributeValues();
        Map<CompanionComputedValue, Computed> computed = menu.computedValues();
        boolean armed = menu.armed();
        Map<ResourceKey<CompanionAttributeGroup>, List<Row>> rowsByGroup = new HashMap<>();
        for (Holder.Reference<CompanionAttribute> holder :
                sorted(registries.lookupOrThrow(CompanionAttribute.REGISTRY), CompanionAttribute::order)) {
            CompanionAttribute entry = holder.value();
            @Nullable Row row = entry.resolve()
                    .map(attribute -> attributeRow(entry, attribute, values.get(attribute), armed))
                    .orElseGet(() -> entry.computed()
                            .map(value -> computedRow(entry, computed.get(value), armed))
                            .orElse(null));
            if (row != null) {
                rowsByGroup.computeIfAbsent(entry.group().unwrapKey().orElseThrow(), key -> new ArrayList<>()).add(row);
            }
        }
        List<Line> lines = new ArrayList<>();
        int y = 0;
        for (Holder.Reference<CompanionAttributeGroup> group :
                sorted(registries.lookupOrThrow(CompanionAttributeGroup.REGISTRY), CompanionAttributeGroup::order)) {
            List<Row> rows = rowsByGroup.get(group.key());
            if (rows == null) {
                continue;
            }
            if (!lines.isEmpty()) {
                y += GROUP_GAP;
            }
            lines.add(new Line(y, null, group.value().heading()));
            for (Row row : rows) {
                y += ROW_HEIGHT;
                lines.add(new Line(y, row, row.label()));
            }
            y += ROW_HEIGHT;
        }
        return lines;
    }

    /** The registry's entries by {@code order}, then by id. */
    private static <T> List<Holder.Reference<T>> sorted(Registry<T> registry, ToIntFunction<T> order) {
        return registry.listElements()
                .sorted(Comparator.<Holder.Reference<T>>comparingInt(holder -> order.applyAsInt(holder.value()))
                        .thenComparing(holder -> holder.key().identifier()))
                .toList();
    }

    private static int contentHeight(List<Line> lines) {
        return lines.isEmpty() ? 0 : lines.getLast().y() + ROW_HEIGHT;
    }

    /**
     * An attribute's row, unless the server sent no value or the entry hides it: its value against
     * the attribute's default, its colour against the entry's threshold or that default.
     */
    private static @Nullable Row attributeRow(
            CompanionAttribute entry, Holder<Attribute> holder, @Nullable Value value, boolean armed) {
        Attribute attribute = holder.value();
        if (value == null || !entry.shows(CompanionAttribute.differsFromDefault(attribute, value.value()), armed)) {
            return null;
        }
        Component name = Component.translatable(attribute.getDescriptionId());
        double reference = attribute.getDefaultValue();
        return new Row(
                entry.label().orElse(name),
                format(entry, value.value(), reference),
                colour(entry.tone(attribute, value.value())),
                List.of(
                        Component.literal(titleCase(name.getString())),
                        Component.translatable(KEY + "current", format(entry, value.value(), reference))
                                .withStyle(ChatFormatting.GRAY),
                        Component.translatable(KEY + "base", format(entry, value.base(), reference))
                                .withStyle(ChatFormatting.GRAY)));
    }

    /** A computed value's row, unless the server sent none or the entry hides it. */
    private static @Nullable Row computedRow(CompanionAttribute entry, @Nullable Computed value, boolean armed) {
        if (value == null) {
            return null;
        }
        return switch (value.value()) {
            case ATTACK_RANGE -> attackRangeRow(entry, value, armed);
        };
    }

    /**
     * The attack range: its maximum, or its minimum and maximum when the weapon cannot hit closer,
     * in the default colour; changed from its default when it is not the bare hand's, up to the
     * interaction range. Its tooltip says how close it cannot hit.
     */
    private static @Nullable Row attackRangeRow(CompanionAttribute entry, Computed range, boolean armed) {
        boolean changed = range.min() > 0.0 || Math.abs(range.max() - range.base()) > 1.0E-6;
        if (!entry.shows(changed, armed)) {
            return null;
        }
        String max = format(entry, range.max(), range.base());
        String text = range.min() > 0.0 ? format(entry, range.min(), range.base()) + EN_DASH + max : max;
        Component name = Component.translatable(KEY + "value.attack_range");
        List<Component> tooltip = new ArrayList<>();
        tooltip.add(name);
        tooltip.add(Component.translatable(KEY + "current", text).withStyle(ChatFormatting.GRAY));
        if (range.min() > 0.0) {
            tooltip.add(Component.translatable(KEY + "value.attack_range.min", format(entry, range.min(), range.base()))
                    .withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable(KEY + "base", format(entry, range.base(), range.base()))
                .withStyle(ChatFormatting.GRAY));
        return new Row(entry.label().orElse(name), text, Theme.TEXT, List.copyOf(tooltip));
    }

    /** {@code value} as the entry's format writes it, {@code reference} its 100%. */
    private static String format(CompanionAttribute entry, double value, double reference) {
        return switch (entry.format()) {
            case PLAIN -> FORMAT.format(value);
            case TIMES_TEN -> FORMAT.format(value * 10.0);
            // A reference of zero has no percentage: the plain value.
            case PERCENT_OF_DEFAULT -> reference == 0.0
                    ? FORMAT.format(value)
                    : FORMAT.format(value / reference * 100.0) + "%";
            case PERCENT -> FORMAT.format(value * 100.0) + "%";
        };
    }

    private static int colour(CompanionAttribute.Tone tone) {
        return switch (tone) {
            case NEUTRAL -> Theme.TEXT;
            case GOOD -> GOOD;
            case BAD -> BAD;
        };
    }

    /** {@code name} capitalised as a title, as vanilla's attribute names are not. */
    private static String titleCase(String name) {
        if (name.isEmpty()) {
            return name;
        }
        int first = name.codePointAt(0);
        return new StringBuilder().appendCodePoint(Character.toTitleCase(first))
                .append(name, Character.charCount(first), name.length())
                .toString();
    }

    /** {@code text}, cut with an ellipsis to fit {@code width}. */
    private static String fit(Font font, Component text, int width) {
        String plain = text.getString();
        if (font.width(plain) <= width) {
            return plain;
        }
        return font.plainSubstrByWidth(plain, Math.max(0, width - font.width(ELLIPSIS))) + ELLIPSIS;
    }
}
