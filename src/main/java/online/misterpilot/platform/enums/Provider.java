package online.misterpilot.platform.enums;

import java.util.Locale;

/**
 * Upstream LLM provider, derived from the model name recorded in
 * {@code token_usage.model}. {@link #OTHER} catches unrecognised models; provider
 * funding splits their spend equally across the known providers.
 */
public enum Provider {
    OPENAI,
    DEEPSEEK,
    ANTHROPIC,
    OTHER;

    public static Provider fromModel(String model) {
        if (model == null) {
            return OTHER;
        }
        String m = model.toLowerCase(Locale.ROOT);
        if (m.startsWith("deepseek")) {
            return DEEPSEEK;
        }
        if (m.startsWith("claude")) {
            return ANTHROPIC;
        }
        if (m.startsWith("gpt") || m.startsWith("chatgpt") || m.startsWith("o1")
                || m.startsWith("o3") || m.startsWith("o4") || m.startsWith("codex")
                || m.startsWith("text-embedding")) {
            return OPENAI;
        }
        return OTHER;
    }
}
