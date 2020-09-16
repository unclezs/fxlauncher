package fxlauncher;

public class Strings {
    public static String ensureEndingSlash(String s) {
        if (s != null && !s.endsWith("/"))
            s += "/";

        return s;
    }

    public static String formatUpdateText(String text) {
        String[] infoItem = text.split("\\|\\|");
        StringBuilder info = new StringBuilder();
        for (String item : infoItem) {
            info.append(item.trim()).append("\n");
        }
        return info.toString();
    }
}
