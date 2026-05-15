package ai.bookmind.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 运行时 AI 提供商配置（支持热切换，无需重启）
 */
@Component
@Slf4j
public class ProviderConfig {

    /** 默认从 yml 读取，可通过 API 动态修改 */
    private String chatProvider = "sensenova";

    public String getChatProvider() {
        return chatProvider;
    }

    public void setChatProvider(String provider) {
        if (!"siliconflow".equals(provider) && !"sensenova".equals(provider)) {
            throw new IllegalArgumentException("不支持的提供商: " + provider + "（可选: siliconflow, sensenova）");
        }
        log.info("AI 提供商热切换: {} → {}", chatProvider, provider);
        this.chatProvider = provider;
    }
}
