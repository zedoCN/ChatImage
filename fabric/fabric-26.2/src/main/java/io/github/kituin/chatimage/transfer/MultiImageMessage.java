package io.github.kituin.chatimage.transfer;

import java.util.*;
import java.util.regex.*;

/** Preserves text and existing CICode wrappers while replacing local attachments. */
public final class MultiImageMessage {
    private record Part(int start, int end, String input, boolean wrapped) {}
    private final String original;
    private final List<Part> parts = new ArrayList<>();
    public MultiImageMessage(String original) {
        this.original = original;
        Matcher matcher = Pattern.compile("file:/+[^,\\]\\s]+").matcher(original);
        while (matcher.find()) {
            int start = matcher.start();
            parts.add(new Part(start, matcher.end(), matcher.group(), original.lastIndexOf("[[CICode,", start) > original.lastIndexOf("]]", start)));
        }
    }
    public int size() { return parts.size(); }
    public String input(int index) { return parts.get(index).input; }
    public String render(List<String> references) {
        if (references.size() != parts.size()) throw new IllegalArgumentException("Attachment count mismatch");
        StringBuilder text = new StringBuilder(); int previous = 0;
        for (int i = 0; i < parts.size(); i++) {
            Part p = parts.get(i); text.append(original, previous, p.start);
            text.append(p.wrapped ? references.get(i) : "[[CICode,url=" + references.get(i) + "]]"); previous = p.end;
        }
        return text.append(original, previous, original.length()).toString();
    }
    public boolean fits() {
        String reference = "mcimage://" + "0".repeat(36) + "/" + "0".repeat(64);
        return render(Collections.nCopies(size(), reference)).length() <= 256;
    }
}
