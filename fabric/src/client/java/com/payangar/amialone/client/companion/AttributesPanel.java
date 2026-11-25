package com.payangar.amialone.client.companion;

import com.payangar.amialone.companion.CompanionMenu;
import com.payangar.amialone.vendor.gui.Draw;
import com.payangar.amialone.vendor.gui.Theme;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.jspecify.annotations.Nullable;

/**
 * The drawer's Attributes panel: the companion's attributes in groups, a label on the left and the
 * value on the right, the full name with the current and base values in a row's tooltip.
 *
 * <p>The list is fixed: the client builds a companion as a player, whose attribute map is a
 * player's, not the companion's. Every value comes from the client companion, which vanilla keeps
 * up to date, except its knockback resistance, which vanilla does not sync and the menu sends. The
 * base shown is the attribute's default, the companion's base: its entity type registers vanilla's
 * living defaults.
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

    /** How a value reads. */
    private enum Format {
        PLAIN,
        /** Tenfold, as vanilla's item tooltips show knockback resistance. */
        TENFOLD,
        /** A percentage of the default: 100% is normal. */
        OF_DEFAULT,
        /** A multiplier as a percentage. */
        PERCENT
    }

    /** A row: always shown, or only when its value differs from the default. */
    private record Row(Holder<Attribute> attribute, String name, Format format, boolean always) {
        Component label() {
            return Component.translatable(KEY + "label." + name);
        }
    }

    private record Group(String name, List<Row> rows) {}

    private static final List<Group> GROUPS = List.of(
            new Group(
                    "defence",
                    List.of(
                            new Row(Attributes.MAX_HEALTH, "max_health", Format.PLAIN, true),
                            new Row(Attributes.ARMOR, "armor", Format.PLAIN, true),
                            new Row(Attributes.ARMOR_TOUGHNESS, "armor_toughness", Format.PLAIN, true),
                            new Row(Attributes.KNOCKBACK_RESISTANCE, "knockback_resistance", Format.TENFOLD, true),
                            new Row(Attributes.MAX_ABSORPTION, "max_absorption", Format.PLAIN, false))),
            new Group(
                    "movement",
                    List.of(
                            new Row(Attributes.MOVEMENT_SPEED, "movement_speed", Format.OF_DEFAULT, true),
                            new Row(Attributes.WATER_MOVEMENT_EFFICIENCY, "water_movement_efficiency", Format.PLAIN, false),
                            new Row(Attributes.MOVEMENT_EFFICIENCY, "movement_efficiency", Format.PLAIN, false),
                            new Row(Attributes.JUMP_STRENGTH, "jump_strength", Format.PLAIN, false),
                            new Row(Attributes.STEP_HEIGHT, "step_height", Format.PLAIN, false))),
            new Group(
                    "hazards",
                    List.of(
                            new Row(Attributes.OXYGEN_BONUS, "oxygen_bonus", Format.PLAIN, false),
                            new Row(Attributes.BURNING_TIME, "burning_time", Format.PERCENT, false),
                            new Row(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE, "explosion_knockback_resistance", Format.PLAIN, false),
                            new Row(Attributes.SAFE_FALL_DISTANCE, "safe_fall_distance", Format.PLAIN, false),
                            new Row(Attributes.FALL_DAMAGE_MULTIPLIER, "fall_damage_multiplier", Format.PERCENT, false))));

    /** A line drawn this frame: a header ({@code row} null) or a row with its values. */
    private record Line(int y, @Nullable Row row, Component text, double value) {}

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
            String value = format(row, line.value());
            int valueWidth = font.width(value);
            graphics.text(font, value, x + width - valueWidth, textY, colour(row, line.value()), false);
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
            graphics.setComponentTooltipForNextFrame(font, tooltip(hovered), mouseX, mouseY);
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

    /** The lines to draw, from the top of the content: each group's header, then its shown rows. */
    private static List<Line> lines(CompanionMenu menu) {
        List<Line> lines = new ArrayList<>();
        int y = 0;
        for (Group group : GROUPS) {
            List<Line> rows = new ArrayList<>();
            for (Row row : group.rows()) {
                double value = value(menu, row);
                if (row.always() || differs(row, value)) {
                    rows.add(new Line(0, row, row.label(), value));
                }
            }
            if (rows.isEmpty()) {
                continue;
            }
            if (!lines.isEmpty()) {
                y += GROUP_GAP;
            }
            lines.add(new Line(y, null, Component.translatable(KEY + "group." + group.name()), 0));
            for (Line row : rows) {
                y += ROW_HEIGHT;
                lines.add(new Line(y, row.row(), row.text(), row.value()));
            }
            y += ROW_HEIGHT;
        }
        return lines;
    }

    private static int contentHeight(List<Line> lines) {
        return lines.isEmpty() ? 0 : lines.getLast().y() + ROW_HEIGHT;
    }

    private static double value(CompanionMenu menu, Row row) {
        return row.attribute() == Attributes.KNOCKBACK_RESISTANCE
                ? menu.knockbackResistance()
                : menu.companion().getAttributeValue(row.attribute());
    }

    private static double base(Row row) {
        return row.attribute().value().getDefaultValue();
    }

    private static boolean differs(Row row, double value) {
        return Math.abs(value - base(row)) > 1.0E-6;
    }

    private static String format(Row row, double value) {
        return switch (row.format()) {
            case PLAIN -> FORMAT.format(value);
            case TENFOLD -> FORMAT.format(value * 10.0);
            case OF_DEFAULT -> FORMAT.format(value / base(row) * 100.0) + "%";
            case PERCENT -> FORMAT.format(value * 100.0) + "%";
        };
    }

    /** The default colour, or, for a changed value, the colour of the change's sentiment. */
    private static int colour(Row row, double value) {
        if (!differs(row, value)) {
            return Theme.TEXT;
        }
        ChatFormatting style = row.attribute().value().getStyle(value > base(row));
        return style == ChatFormatting.BLUE ? GOOD : style == ChatFormatting.RED ? BAD : Theme.TEXT;
    }

    /** The attribute's full vanilla name, capitalised as a title, then its current and base values. */
    private static List<Component> tooltip(Line line) {
        Row row = line.row();
        String name = Component.translatable(row.attribute().value().getDescriptionId()).getString();
        if (!name.isEmpty()) {
            int first = name.codePointAt(0);
            name = new StringBuilder().appendCodePoint(Character.toTitleCase(first))
                    .append(name, Character.charCount(first), name.length())
                    .toString();
        }
        return List.of(
                Component.literal(name),
                Component.translatable(KEY + "current", format(row, line.value())).withStyle(ChatFormatting.GRAY),
                Component.translatable(KEY + "base", format(row, base(row))).withStyle(ChatFormatting.GRAY));
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
