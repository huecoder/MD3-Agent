package dev.huecoder.md3assistant.llm.models

enum class ModelVendor(val displayName: String) {
    ANTHROPIC("Anthropic"),
    OPENAI("OpenAI"),
    GOOGLE("Google"),
    META("Meta"),
    MISTRAL("Mistral"),
    DEEPSEEK("DeepSeek"),
    ALIBABA("Alibaba"),
    MOONSHOT("Moonshot AI"),
    ZAI("Z.ai"),
    XAI("xAI"),
    PERPLEXITY("Perplexity"),
    MINIMAX("MiniMax"),
    BYTEDANCE("ByteDance"),
    XIAOMI("Xiaomi"),
    STEPFUN("StepFun"),
    NVIDIA("Nvidia"),
    COHERE("Cohere"),
    MICROSOFT("Microsoft"),
}

enum class ModelFamily(val vendor: ModelVendor, val displayName: String, pattern: String) {
    NEMOTRON(ModelVendor.NVIDIA, "Nemotron", "nemotron|nvidia"),
    DEEPSEEK(ModelVendor.DEEPSEEK, "DeepSeek", "deepseek"),
    SONAR(ModelVendor.PERPLEXITY, "Sonar", "sonar|perplexity|r1-1776"),
    CLAUDE(ModelVendor.ANTHROPIC, "Claude", "claude|anthropic"),
    GEMMA(ModelVendor.GOOGLE, "Gemma", "gemma"),
    GEMINI(ModelVendor.GOOGLE, "Gemini", "gemini|nano-banana|imagen|veo|learnlm|google"),
    MUSE(ModelVendor.META, "Muse", "muse"),
    LLAMA(ModelVendor.META, "Llama", "llama|meta-llama"),
    MISTRAL(ModelVendor.MISTRAL, "Mistral", "mistral|mixtral|codestral|pixtral|magistral|devstral|ministral|voxtral"),
    KIMI(ModelVendor.MOONSHOT, "Kimi", "kimi|moonshot"),
    GLM(ModelVendor.ZAI, "GLM", "glm|chatglm|z-ai|zai|zhipu|cogview"),
    GROK(ModelVendor.XAI, "Grok", "grok|x-ai|xai"),
    MINIMAX(ModelVendor.MINIMAX, "MiniMax", "minimax|abab|hailuo"),
    SEED(ModelVendor.BYTEDANCE, "Seed", "doubao|seed|seedream|seedance|bytedance|ui-tars"),
    MIMO(ModelVendor.XIAOMI, "MiMo", "mimo|xiaomi"),
    STEP(ModelVendor.STEPFUN, "Step", "step-?[0-9]|stepfun"),
    COMMAND(ModelVendor.COHERE, "Command", "command|cohere|aya"),
    PHI(ModelVendor.MICROSOFT, "Phi", "phi-?[0-9]|microsoft|wizardlm"),
    QWEN(ModelVendor.ALIBABA, "Qwen", "qwen|qwq|qvq|wan[0-9]|alibaba"),
    GPT(ModelVendor.OPENAI, "GPT", "chatgpt|gpt|o[1-9]|codex|dall-e|sora|openai|whisper|tts-1"),
    ;

    private val regex = Regex("(?<![a-z0-9])(?:$pattern)")

    companion object {
        fun detect(modelId: String): ModelFamily? {
            val normalized = modelId.lowercase()
            val leaf = normalized.substringAfterLast('/')
            return entries.firstOrNull { it.regex.containsMatchIn(leaf) } ?: entries.firstOrNull { it.regex.containsMatchIn(normalized) }
        }
    }
}

internal data class HeuristicTraits(val vision: Boolean, val reasoning: Boolean, val output: ModelOutput)

internal object ModelHeuristics {
    private val NATIVE_IMAGE_CHAT = Regex(
        "nano-banana|gemini-[0-9.]+-(flash|pro)-image|gpt-image|gpt-[0-9.]+-image|muse-image|grok-[0-9.]*-?image|qwen-image-edit",
    )
    private val IMAGE_ONLY = Regex(
        "dall-e|imagen|(?<![a-z])flux|seedream|stable-diffusion|sdxl|sd3|midjourney|recraft|ideogram|cogview|hidream|qwen-image|hunyuan-image|kolors|playground-v",
    )
    private val REASONING = Regex(
        "(?<![a-z0-9])o[1-9](?![0-9])|gpt-5|gpt-oss|thinking|reason|(?<![a-z0-9])r1(?![0-9])|qwq|qvq|magistral|" +
            "claude-(3[.-]7|opus-4|sonnet-4|haiku-4|(opus|sonnet|haiku)-[5-9]|[4-9])|gemini-(2[.]5|[3-9])|grok-(3-mini|[4-9])|" +
            "kimi-k2[.-]?(5|thinking)|glm-(4[.][5-9]|[5-9])|qwen3|qwen-?[4-9]|deepseek-(v3[.][1-9]|v[4-9])|minimax-m|" +
            "nemotron.*(super|ultra|nano)|mimo|step-?3|seed-?[1-9]|muse-spark|phi-4-(mini-)?reasoning",
    )
    private val VISION = Regex(
        "gpt-4o|gpt-4[.]1|gpt-4-turbo|gpt-5|(?<![a-z0-9])o[34](?![0-9])|claude-(3|sonnet|opus|haiku|[4-9])|gemini|gemma-?[3-9]|" +
            "llama-?4|llama-3[.]2-.*vision|pixtral|mistral-(small|medium)-3|(?<![a-z])vl(?![a-z])|vision|qvq|omni|kimi-k2[.]5|" +
            "grok-[4-9]|glm-[0-9.]+v|muse|step-?3|seed-?1[.][5-9]|nova-(lite|pro|premier)|phi-4-multimodal|minimax-(m2[.]5|vl)",
    )

    fun guess(modelId: String): HeuristicTraits {
        val id = modelId.lowercase()
        val output = when {
            NATIVE_IMAGE_CHAT.containsMatchIn(id) -> ModelOutput.TEXT_AND_IMAGE
            IMAGE_ONLY.containsMatchIn(id) -> ModelOutput.IMAGE
            else -> ModelOutput.TEXT
        }
        return HeuristicTraits(
            vision = VISION.containsMatchIn(id),
            reasoning = output != ModelOutput.IMAGE && REASONING.containsMatchIn(id),
            output = output,
        )
    }
}
