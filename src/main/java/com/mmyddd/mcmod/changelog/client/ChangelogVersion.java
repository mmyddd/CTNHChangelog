package com.mmyddd.mcmod.changelog.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ChangelogVersion {
    private ChangelogVersion() {
    }

    public static boolean isNewer(String candidateVersion, String currentVersion) {
        return compare(candidateVersion, currentVersion) > 0;
    }

    public static int compare(String leftVersion, String rightVersion) {
        ParsedVersion left = parse(leftVersion);
        ParsedVersion right = parse(rightVersion);

        int baseComparison = compareBase(left.baseTokens, right.baseTokens);
        if (baseComparison != 0) {
            return baseComparison;
        }

        if (left.hasPrerelease != right.hasPrerelease) {
            return left.hasPrerelease ? -1 : 1;
        }
        if (!left.hasPrerelease) {
            return 0;
        }
        return comparePrerelease(left.prereleaseTokens, right.prereleaseTokens);
    }

    private static ParsedVersion parse(String version) {
        String normalized = version == null ? "" : version.trim();
        if (normalized.startsWith("v") || normalized.startsWith("V")) {
            normalized = normalized.substring(1);
        }

        int buildMetadataIndex = normalized.indexOf('+');
        if (buildMetadataIndex >= 0) {
            normalized = normalized.substring(0, buildMetadataIndex);
        }

        int prereleaseIndex = normalized.indexOf('-');
        boolean hasPrerelease = prereleaseIndex >= 0;
        String base = hasPrerelease ? normalized.substring(0, prereleaseIndex) : normalized;
        String prerelease = hasPrerelease ? normalized.substring(prereleaseIndex + 1) : "";
        return new ParsedVersion(tokenize(base), tokenize(prerelease), hasPrerelease);
    }

    private static List<VersionToken> tokenize(String value) {
        List<VersionToken> tokens = new ArrayList<>();
        int index = 0;
        while (index < value.length()) {
            char current = value.charAt(index);
            if (!Character.isLetterOrDigit(current)) {
                index++;
                continue;
            }

            boolean numeric = Character.isDigit(current);
            int tokenStart = index++;
            while (index < value.length()) {
                char next = value.charAt(index);
                if (!Character.isLetterOrDigit(next) || Character.isDigit(next) != numeric) {
                    break;
                }
                index++;
            }
            tokens.add(new VersionToken(value.substring(tokenStart, index), numeric));
        }
        return tokens;
    }

    private static int compareBase(List<VersionToken> leftTokens, List<VersionToken> rightTokens) {
        int tokenCount = Math.max(leftTokens.size(), rightTokens.size());
        for (int index = 0; index < tokenCount; index++) {
            VersionToken left = index < leftTokens.size() ? leftTokens.get(index) : null;
            VersionToken right = index < rightTokens.size() ? rightTokens.get(index) : null;
            if (left == null || right == null) {
                if (left == null && right == null) {
                    continue;
                }
                VersionToken present = left == null ? right : left;
                if (present.numeric && isZero(present.value)) {
                    continue;
                }
                return left == null ? -1 : 1;
            }

            int comparison = compareTokens(left, right, false);
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }

    private static int comparePrerelease(List<VersionToken> leftTokens, List<VersionToken> rightTokens) {
        int tokenCount = Math.max(leftTokens.size(), rightTokens.size());
        for (int index = 0; index < tokenCount; index++) {
            VersionToken left = index < leftTokens.size() ? leftTokens.get(index) : null;
            VersionToken right = index < rightTokens.size() ? rightTokens.get(index) : null;
            if (left == null || right == null) {
                if (left == null && right == null) {
                    continue;
                }
                return left == null ? -1 : 1;
            }

            int comparison = compareTokens(left, right, true);
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }

    private static int compareTokens(VersionToken left, VersionToken right, boolean prerelease) {
        if (left.numeric && right.numeric) {
            return compareNumbers(left.value, right.value);
        }
        if (left.numeric != right.numeric) {
            if (prerelease) {
                return left.numeric ? -1 : 1;
            }
            return left.numeric ? 1 : -1;
        }
        return left.value.toLowerCase(Locale.ROOT).compareTo(right.value.toLowerCase(Locale.ROOT));
    }

    private static boolean isZero(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) != '0') {
                return false;
            }
        }
        return true;
    }

    private static int compareNumbers(String leftNumber, String rightNumber) {
        String normalizedLeft = trimLeadingZeroes(leftNumber);
        String normalizedRight = trimLeadingZeroes(rightNumber);
        int lengthComparison = Integer.compare(normalizedLeft.length(), normalizedRight.length());
        return lengthComparison != 0 ? lengthComparison : normalizedLeft.compareTo(normalizedRight);
    }

    private static String trimLeadingZeroes(String value) {
        int firstNonZero = 0;
        while (firstNonZero < value.length() - 1 && value.charAt(firstNonZero) == '0') {
            firstNonZero++;
        }
        return value.substring(firstNonZero);
    }

    private static final class ParsedVersion {
        private final List<VersionToken> baseTokens;
        private final List<VersionToken> prereleaseTokens;
        private final boolean hasPrerelease;

        private ParsedVersion(List<VersionToken> baseTokens, List<VersionToken> prereleaseTokens,
                              boolean hasPrerelease) {
            this.baseTokens = baseTokens;
            this.prereleaseTokens = prereleaseTokens;
            this.hasPrerelease = hasPrerelease;
        }
    }

    private static final class VersionToken {
        private final String value;
        private final boolean numeric;

        private VersionToken(String value, boolean numeric) {
            this.value = value;
            this.numeric = numeric;
        }
    }
}