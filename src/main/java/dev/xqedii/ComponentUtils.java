package dev.xqedii;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;

public class ComponentUtils {
    public static String toPlainText(Component component) {
        if (component == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        if (component instanceof TextComponent) {
            sb.append(((TextComponent) component).content());
        }

        for (Component child : component.children()) {
            sb.append(toPlainText(child));
        }

        return sb.toString();
    }
}