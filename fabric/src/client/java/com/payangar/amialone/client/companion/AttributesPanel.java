package com.payangar.amialone.client.companion;

import com.payangar.amialone.companion.CompanionAttribute;
import com.payangar.amialone.companion.CompanionAttributeGroup;
import com.payangar.amialone.companion.CompanionAttributeValues.Value;
import com.payangar.amialone.companion.CompanionMenu;
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
 * The drawer's Attributes panel: the companion's attributes in groups, a label on the left and the
 * value on the right, the full name with the current and base values in a row's tooltip.
 *
 * <p>What it shows is data: the groups and the attributes of the {@link CompanionAttributeGroup}
 * and {@link CompanionAttribute} registries, each in its order. The values are the ones the server
 * sent the menu, never the client companion's: the client builds a companion as a player, whose
 * attribute map is a player's. An attribute the server sent no value for is not shown.
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

    /** A row: an entry, its attribute and the values the server sent. */
    private record Row(CompanionAttribute entry, Holder<Attribute> attribute, Value values) {
        double value() {
            return values.value();
        }

        double base() {
            return values.base();
        }

        /** The entry's short label, or the attribute's own name. */
        Component label() {
            return entry.label().orElseGet(() -> Component.translatable(attribute.value().getDescriptionId()));
        }

        boolean changed() {
            return Math.abs(value() - base()) > 1.0E-6;
        }
    }

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
            CompanionMenu menu,
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
            String value = format(row, row.value());
            int valueWidth = font.width(value);
            graphics.text(font, value, x + width - valueWidth, textY, colour(row), false);
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
            graphics.setComponentTooltipForNextFrame(font, tooltip(hovered.row()), mouseX, mouseY);
        }
    }

    /** Scrolls the rows by the wheel's {@code amount}; false when they fit and do not scroll. */
    boolean scroll(CompanionMenu menu, int height, double amount) {
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
    private static List<Line> lines(CompanionMenu menu) {
        RegistryAccess registries = menu.companion().registryAccess();
        Map<Holder<Attribute>, Value> values = menu.attributeValues();
        Map<ResourceKey<CompanionAttributeGroup>, List<Row>> rowsByGroup = new HashMap<>();
        for (Holder.Reference<CompanionAttribute> holder :
                sorted(registries.lookupOrThrow(CompanionAttribute.REGISTRY), CompanionAttribute::order)) {
            CompanionAttribute entry = holder.value();
            @Nullable Holder<Attribute> attribute = entry.resolve().orElse(null);
            @Nullable Value value = attribute == null ? null : values.get(attribute);
            if (value == null) {
                continue;
            }
            Row row = new Row(entry, attribute, value);
            if (entry.shown() == CompanionAttribute.Shown.ALWAYS || row.changed()) {
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

    private static String format(Row row, double value) {
        return switch (row.entry().format()) {
            case PLAIN -> FORMAT.format(value);
            case TIMES_TEN -> FORMAT.format(value * 10.0);
            // A base of zero has no percentage: the plain value.
            case PERCENT_OF_DEFAULT -> row.base() == 0.0
                    ? FORMAT.format(value)
                    : FORMAT.format(value / row.base() * 100.0) + "%";
            case PERCENT -> FORMAT.format(value * 100.0) + "%";
        };
    }

    /** The default colour, or, for a changed value, the colour of the change's sentiment. */
    private static int colour(Row row) {
        if (!row.changed()) {
            return Theme.TEXT;
        }
        ChatFormatting style = row.attribute().value().getStyle(row.value() > row.base());
        return style == ChatFormatting.BLUE ? GOOD : style == ChatFormatting.RED ? BAD : Theme.TEXT;
    }

    /** The attribute's full vanilla name, capitalised as a title, then its current and base values. */
    private static List<Component> tooltip(Row row) {
        String name = Component.translatable(row.attribute().value().getDescriptionId()).getString();
        if (!name.isEmpty()) {
            int first = name.codePointAt(0);
            name = new StringBuilder().appendCodePoint(Character.toTitleCase(first))
                    .append(name, Character.charCount(first), name.length())
                    .toString();
        }
        return List.of(
                Component.literal(name),
                Component.translatable(KEY + "current", format(row, row.value())).withStyle(ChatFormatting.GRAY),
                Component.translatable(KEY + "base", format(row, row.base())).withStyle(ChatFormatting.GRAY));
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
